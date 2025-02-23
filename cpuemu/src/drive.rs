use anyhow::Context;
use elf::{
  abi::{EM_RISCV, ET_EXEC, PT_LOAD, STT_FUNC},
  endian::LittleEndian,
  ElfStream,
};
use regex::Captures;
use riscv_isa::{decode_full, Decoder, Instruction, Target};
use std::collections::HashMap;
use std::os::unix::fs::FileExt;
use std::str::FromStr;
use std::{fs, path::Path};
use svdpi::{get_time, SvScope};
use tracing::{debug, error, info, trace};

#[cfg(feature = "trace")]
use crate::dpi::dump_wave;
use crate::{
  bus::ShadowBus,
  dpi::{AxiReadPayload, RetireData},
  ref_module::{self, nemu::NemuEvent, RefModule},
  SimArgs,
};

#[derive(Debug)]
#[allow(dead_code)]
pub struct FunctionSym {
  #[allow(dead_code)]
  pub(crate) name: String,
  #[allow(dead_code)]
  pub(crate) info: u8,
}
pub type FunctionSymTab = HashMap<u64, FunctionSym>;

#[repr(u8)]
#[derive(Copy, Clone, PartialEq)]
pub enum SimState {
  Running = 0,
  GoodTrap,
  BadTrap,
  Timeout,
  Finished,
}

const EXIT_POS: u32 = 0x4000_0000;
const EXIT_CODE: u32 = 0xdead_beef;

pub(crate) struct Driver {
  #[cfg(feature = "difftest")]
  refmodule: RefModule,

  bus: ShadowBus,

  #[cfg(feature = "trace")]
  dump_control: DumpControl,

  pub(crate) e_entry: u64,
  //pub(crate) data_width: u64,
  pub(crate) timeout: u64,
  pub(crate) clock_flip_time: u64,
  last_commit_cycle: u64,

  pub(crate) state: SimState,

  pub(crate) dlen: u32,

  pub(crate) pc: u64,
  pub(crate) gpr: [u64; 32],
  pub(crate) a0: u64, //check return
  skip: bool,
  target: Target,
}

static MAX_TIME: u64 = 10000000;

impl Driver {
  fn get_tick(&self) -> u64 {
    get_time() / self.clock_flip_time
  }

  pub(crate) fn new(scope: SvScope, args: &SimArgs) -> Self {
    let (e_entry, shadow_bus, _fn_sym_tab, refmodule) =
      Self::load_elf(&args.elf_file).expect("fail creating simulator");

    //refmodule.display();

    let self_ = Self {
      #[cfg(feature = "difftest")]
      refmodule,
      bus: shadow_bus,
      #[cfg(feature = "trace")]
      dump_control: DumpControl::new(scope, &args.wave_path, args.dump_start, args.dump_end),
      e_entry,
      //data_width: env!("DESIGN_DATA_WIDTH").parse().unwrap(),
      timeout: env!("DESIGN_TIMEOUT").parse().unwrap(),
      clock_flip_time: env!("CLOCK_FLIP_TIME").parse().unwrap(),
      last_commit_cycle: 0,
      state: SimState::Running,
      dlen: 64,
      pc: 0x8000_0000,
      gpr: [0; 32],
      a0: 0,
      skip: false,
      target: Target::from_str("RV64IMACZifencei_Zicsr").unwrap(),
    };
    self_
  }
  pub fn load_elf(path: &Path) -> anyhow::Result<(u64, ShadowBus, FunctionSymTab, RefModule)> {
    let file = fs::File::open(path).with_context(|| "reading ELF file")?;
    let mut elf: ElfStream<LittleEndian, _> =
      ElfStream::open_stream(&file).with_context(|| "parsing ELF file")?;

    if elf.ehdr.e_machine != EM_RISCV {
      anyhow::bail!("ELF is not in RISC-V");
    }

    if elf.ehdr.e_type != ET_EXEC {
      anyhow::bail!("ELF is not an executable");
    }

    if elf.ehdr.e_phnum == 0 {
      anyhow::bail!("ELF has zero size program header");
    }

    debug!("ELF entry: 0x{:x}", elf.ehdr.e_entry);
    let mut mem = ShadowBus::new();
    let mut load_buffer = Vec::new();
    //#[cfg(feature = "difftest")]
    let mut refmodule = RefModule::new();

    elf.segments().iter().filter(|phdr| phdr.p_type == PT_LOAD).for_each(|phdr| {
      let vaddr: usize = phdr.p_vaddr.try_into().expect("fail converting vaddr(u64) to usize");
      let filesz: usize = phdr.p_filesz.try_into().expect("fail converting p_filesz(u64) to usize");
      debug!(
        "Read loadable segments 0x{:x}..0x{:x} to memory 0x{:x}",
        phdr.p_offset,
        phdr.p_offset + filesz as u64,
        vaddr
      );

      // Load file start from offset into given mem slice
      // The `offset` of the read_at method is relative to the start of the file and thus independent from the current cursor.
      load_buffer.resize(filesz, 0u8);
      file.read_at(load_buffer.as_mut_slice(), phdr.p_offset).unwrap_or_else(|err| {
        panic!(
          "fail reading ELF into mem with vaddr={}, filesz={}, offset={}. Error detail: {}",
          vaddr, filesz, phdr.p_offset, err
        )
      });
      mem.load_mem_seg(vaddr, load_buffer.as_mut_slice());
      #[cfg(feature = "difftest")]
      refmodule.load_mem_seg(vaddr, load_buffer.as_mut_slice());
    });

    // FIXME: now the symbol table doesn't contain any function value
    let mut fn_sym_tab = FunctionSymTab::new();
    let symbol_table =
      elf.symbol_table().with_context(|| "reading symbol table(SHT_SYMTAB) from ELF")?;
    if let Some((parsed_table, string_table)) = symbol_table {
      parsed_table
        .iter()
        // st_symtype = symbol.st_info & 0xf (But why masking here?)
        .filter(|sym| sym.st_symtype() == STT_FUNC)
        .for_each(|sym| {
          let name = string_table
            .get(sym.st_name as usize)
            .unwrap_or_else(|_| panic!("fail to get name at st_name={}", sym.st_name));
          fn_sym_tab.insert(
            sym.st_value,
            FunctionSym { name: name.to_string(), info: sym.st_symtype() },
          );
        });
    } else {
      debug!("load_elf: symtab not found");
    };

    Ok((elf.ehdr.e_entry, mem, fn_sym_tab, refmodule))
  }

  pub(crate) fn axi_read(&mut self, addr: u32, arsize: u64) -> anyhow::Result<AxiReadPayload> {
    let size = 1 << arsize;
    let data = self.bus.read_mem_axi(addr, size, self.dlen / 8)?;
    let data_hex = hex::encode(&data);
    unsafe {
      use crate::dpi::LAST_READ_PC;
      if addr as u64 != LAST_READ_PC {
        trace!(
          "\x1b[34m[{}]\x1b[0m \x1b[35maxi_read\x1b[0m   (addr=\x1b[36m{addr:#x}\x1b[0m, size=\x1b[35m{size}\x1b[0m, data=\x1b[33m{data_hex}\x1b[0m)", 
          //"[{}] axi_read  (addr={addr:#x}, size={size}, data={data_hex})",
          self.get_tick()
        );
      }
    }
    Ok(AxiReadPayload { data })
  }

  pub(crate) fn axi_write(
    &mut self,
    addr: u32,
    awsize: u64,
    strobe: &[bool],
    data: &[u8],
  ) -> anyhow::Result<()> {
    let size = 1 << awsize;
    // check exit with code
    if addr == EXIT_POS {
      let exit_data_slice = data[..4].try_into().expect("slice with incorrect length");
      if u32::from_le_bytes(exit_data_slice) == EXIT_CODE {
        info!("driver is ready to quit");
        self.state = SimState::Finished;
        return Ok(());
      }
    }

    self.bus.write_mem_axi(addr, size, self.dlen / 8, strobe, data)?;
    let data_hex = hex::encode(data);
    self.last_commit_cycle = self.get_tick();

    unsafe {
      use crate::dpi::LAST_WRITE_PC;
      if addr as u64 != LAST_WRITE_PC {
        trace!(
          "\x1b[34m[{}]\x1b[0m \x1b[33maxi_write\x1b[0m (addr=\x1b[36m{addr:#x}\x1b[0m, size={size}, data=\x1b[32m{data_hex}\x1b[0m)", 
          //"[{}] axi_write (addr={addr:#x}, size={size}, data={data_hex})",
          self.get_tick()
        );
      }
    }

    Ok(())
  }

  pub(crate) fn watchdog(&mut self) -> u8 {
    self.state = match self.state {
      SimState::Running => {
        //check timeout
        let tick = self.get_tick();
        if tick - self.last_commit_cycle > self.timeout {
          error!(
            "[{tick}] watchdog timeout (last_commit_cycle={})",
            self.last_commit_cycle
          );
          SimState::Timeout
        } else if tick > MAX_TIME {
          error!("[{tick}] watchdog timeout (MAX_TIME={})", MAX_TIME);
          SimState::Timeout
        } else {
          //check dump end
          #[cfg(feature = "trace")]
          if self.dump_control.isend() {
            SimState::Finished
          } else {
            #[cfg(feature = "trace")]
            self.dump_control.try_start(tick);

            //trace!("[{tick}] watchdog continue");
            SimState::Running
          }

          #[cfg(not(feature = "trace"))]
          {
            //trace!("[{tick}] watchdog continue");
            SimState::Running
          }
        }
      }
      _ => self.state,
    };
    self.state as u8
  }

  pub(crate) fn disasm(&mut self, inst: u32, gpr: [u64; 32]) -> String {
    let raw = decode_full(inst, &self.target).to_string();
    let re = regex::Regex::new(
      r"(?x)
        (\b\d+\b)       # 匹配纯数字offset(第1捕获组)
        \(
          (x\d+)        # 匹配基址寄存器(第2捕获组)
        \)
        |               # 或
        (x\d+)          # 单独寄存器(第3捕获组)
        |               # 或
        (\b0x[\da-fA-F]+\b) # 16进制立即数(第4捕获组)
    ",
    )
    .unwrap();
    // 替换为寄存器值
    re.replace_all(&raw, |caps: &Captures| {
      if let Some(offset) = caps.get(1) {
        // 处理内存访问格式 64(x2)
        let base_reg = caps.get(2).unwrap().as_str();
        let reg_num: usize = base_reg[1..].parse().unwrap();
        let base_val = gpr[reg_num];
        let offset_num: u64 = offset.as_str().parse().unwrap();
        let addr = base_val.wrapping_add(offset_num);

        format!(
          "{}({}<{:#x}>)=<{:#x}>",
          offset.as_str(),
          base_reg,
          base_val,
          addr
        )
      } else if let Some(reg) = caps.get(3) {
        // 处理单独寄存器 x8
        let reg_num: usize = reg.as_str()[1..].parse().unwrap();
        let value = gpr[reg_num];
        format!("x{}<{:#x}>", reg_num, value)
      } else if let Some(hex_num) = caps.get(4) {
        // 保留16进制立即数的原始格式
        hex_num.as_str().to_string()
      } else {
        caps[0].to_string()
      }
    })
    .to_string()
  }

  pub(crate) fn retire_instruction(&mut self, dut: &RetireData) {
    self.last_commit_cycle = self.get_tick();

    // 避免输出多次
    if self.state != SimState::Running {
      return;
    }

    #[cfg(not(feature = "difftest"))]
    {
      return;
    }

    #[cfg(feature = "difftest")]
    {
      use crate::ref_module::{csr_name, gpr_name};

      if dut.skip {
        self.skip = true;
        return;
      }
      if self.skip {
        let event = NemuEvent { gpr: dut.gpr, csr: dut.csr, pc: dut.pc };
        self.refmodule.override_event(event);
      }

      let ref_event = self.refmodule.step();

      // load & store
      // if dut.is_load || dut.is_store {

      // }
      // check reg
      let mut mismatch = false;
      let ref_next_pc = ref_event.pc;
      let ref_gpr = ref_event.gpr;
      let ref_csr = ref_event.csr;

      let dut_pc = dut.pc;
      let dut_gpr = dut.gpr;
      let dut_csr = dut.csr;
      let dut_inst = dut.inst;

      //check pc
      if !self.skip {
        if self.pc != dut_pc {
          error!("pc mismatch! ref={:#x}, dut={:#x}", self.pc, dut_pc);
          mismatch = true;
        }
      }

      //check gpr
      for i in 0..32 {
        if ref_gpr[i] != dut_gpr[i] {
          error!(
            "gpr{}({}) mismatch! ref={:#x}, dut={:#x}",
            i,
            gpr_name(i),
            ref_gpr[i],
            dut_gpr[i]
          );
          mismatch = true;
        }
      }

      //check csr
      for i in 0..18 {
        let ref_csr = ref_csr[i];
        let dut_csr = dut_csr[i];
        if ref_csr != dut_csr {
          error!(
            "csr{} mismatch! ref={:#x}, dut={:#x}",
            csr_name(i),
            ref_csr,
            dut_csr
          );
          mismatch = true;
        }
      }

      if mismatch {
        error!(
          "[{}]dut pc: {:#018x}, inst: {:#010x}[{}]",
          self.get_tick(),
          dut_pc,
          dut_inst,
          self.disasm(dut_inst, self.gpr)
        );
        error!("ref display:");
        self.refmodule.display();
        error!("difftest mismatch!");
        self.state = SimState::BadTrap;
      }
      self.pc = ref_next_pc;
      self.gpr = dut_gpr;
      self.a0 = ref_gpr[10];
      self.skip = false;
    }
  }
}

#[cfg(feature = "trace")]
pub struct DumpControl {
  svscope: svdpi::SvScope,
  wave_path: String,
  dump_start: u64,
  dump_end: u64,

  dump_started: bool,
}

#[cfg(feature = "trace")]
impl DumpControl {
  fn new(svscope: SvScope, wave_path: &str, dump_start: u64, dump_end: u64) -> Self {
    Self {
      svscope,
      wave_path: wave_path.to_owned(),
      dump_start,
      dump_end,

      dump_started: false,
    }
  }

  pub fn start(&mut self) {
    if !self.dump_started {
      dump_wave(self.svscope, &self.wave_path);
      self.dump_started = true;
    }
  }

  pub fn isend(&self) -> bool {
    self.dump_end != 0 && get_time() > self.dump_end
  }

  pub fn try_start(&mut self, tick: u64) {
    if tick >= self.dump_start {
      self.start();
    }
  }
}
