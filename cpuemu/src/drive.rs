mod RefModule;
use RefModule::*;

use svdpi::{get_time, SvScope};
use anyhow::Context;
use elf::{
  abi::{EM_RISCV, ET_EXEC, PT_LOAD, STT_FUNC},
  endian::LittleEndian,
  ElfStream,
};
use std::collections::HashMap;
use std::{fs, path::Path};
use tracing::{debug, error, info, trace};
use std::os::unix::fs::FileExt;

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
#[derive(Copy, Clone)]
pub enum SimState {
    Finished = 0,
    Timeout,
    Running,
}

const EXIT_POS: u32 = 0x4000_0000;
const EXIT_CODE: u32 = 0xdead_beef;

pub(crate) struct Driver {
  #[cfg(feature = "difftest")]
  refmodule: RefModule,

  bus: ShadowBus,

  scope: SvScope,

  #[cfg(feature = "trace")]
  dump_control: DumpControl,

  pub(crate) e_entry: u64,
  //pub(crate) data_width: u64,
  pub(crate) timeout: u64,
  pub(crate) clock_flip_time: u64,
  last_commit_cycle: u64,

  pub(crate) state: SimState,
}

impl Driver {
  fn get_tick(&self) -> u64 {
    get_time() / self.clock_flip_time
  }

  pub(crate) fn new(scope: SvScope, args: &SimArgs) -> Self {
    let (e_entry, shadow_bus, _fn_sym_tab, refmodule) =
      Self::load_elf(&args.elf_file).expect("fail creating simulator");

    let mut self_ = Self {
      #[cfg(feature = "difftest")]
      refmodule,
      bus: shadow_bus,
      scope,
      #[cfg(feature = "trace")]
      dump_control: DumpControl::new(scope, &args.wave_path, args.dump_start, args.dump_end),
      e_entry,
      //data_width: env!("DESIGN_DATA_WIDTH").parse().unwrap(),
      timeout: env!("DESIGN_TIMEOUT").parse().unwrap(),
      clock_flip_time: env!("CLOCK_FLIP_TIME").parse().unwrap(),
      last_commit_cycle: 0,
      state: SimState::Running,
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
    #[cfg(feature = "difftest")]
    let refmodule = RefModule::new();

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
      refmodule.load_elf_seg(vaddr, load_buffer.as_mut_slice());
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

  pub(crate) fn axi_read_load_store(&mut self, addr: u32, arsize: u64) -> AxiReadPayload {
    let size = 1 << arsize;
    let bus_size = if size == 32 { 32 } else { 4 };
    let data = self.bus.read_mem_axi(addr, size, bus_size);
    let data_hex = hex::encode(&data);
    self.last_commit_cycle = get_t();
    trace!(
      "[{}] axi_read_load_store (addr={addr:#x}, size={size}, data={data_hex})",
      get_t()
    );
    AxiReadPayload { data }
  }

  pub(crate) fn axi_write_load_store(
    &mut self,
    addr: u32,
    awsize: u64,
    strobe: &[bool],
    data: &[u8],
  ) {
    let size = 1 << awsize;
    let bus_size = if size == 32 { 32 } else { 4 };
    self.bus.write_mem_axi(addr, size, bus_size, strobe, data);
    let data_hex = hex::encode(data);
    self.last_commit_cycle = get_t();

    trace!(
      "[{}] axi_write_load_store (addr={addr:#x}, size={size}, data={data_hex})",
      get_t()
    );

    // check exit with code
    if addr == EXIT_POS {
      let exit_data_slice = data[..4].try_into().expect("slice with incorrect length");
      if u32::from_le_bytes(exit_data_slice) == EXIT_CODE {
        info!("driver is ready to quit");
        self.state = SimState::Finished;
      }
    }
  }

  pub(crate) fn axi_read_instruction_fetch(&mut self, addr: u32, arsize: u64) -> AxiReadPayload {
    let size = 1 << arsize;
    let data = self.bus.read_mem_axi(addr, size, 32);
    let data_hex = hex::encode(&data);
    trace!(
      "[{}] axi_read_instruction_fetch (addr={addr:#x}, size={size}, data={data_hex})",
      get_t()
    );
    AxiReadPayload { data }
  }

  pub(crate) fn watchdog(&mut self) -> u8 {
    self.state = match self.state {
        SimState::Finished => SimState::Finished,
        SimState::Timeout => SimState::Timeout,
        SimState::Running => {
            //check timeout
            let tick = self.get_tick();
            if tick - self.last_commit_cycle > self.timeout {
                error!("[{tick}] watchdog timeout (last_commit_cycle={}", self.last_commit_cycle);
                SimState::Timeout
            } else {
                //check dump end
                #[cfg(feature = "trace")]
                if self.dump_control.isend() {
                    SimState::Finished
                } else {
                    #[cfg(feature = "trace")]
                    self.dump_control.try_start(tick);

                    trace!("[{tick}] watchdog continue");
                    SimState::Running
                }
                
                #[cfg(not(feature = "trace"))]
                {
                    trace!("[{tick}] watchdog continue");
                    SimState::Running
                }
            }
        }
    };
    self.state as u8
  }

  #[cfg(feature = "difftest")]
  pub(crate) fn retire_instruction(&mut self, dut: &RetireData) {
    let ref_event = self.refmodule.step();

    if dut.skip {
      let event = NemuEvent { gpr: dut.gpr, csr: dut.csr, pc: dut.pc };
      self.refmodule.override_event(event);
      return;
    }

    let mut mismatch = false;
    //check gpr
    for i in 0..32 {
      if ref_event.gpr[i] != dut.gpr[i] {
        println!(
          "gpr{} mismatch! ref={:#x}, dut={:#x}",
          i, ref_event.gpr[i], dut.gpr[i]
        );
        mismatch = true;
      }
    }

    //check pc
    if ref_event.pc != dut.pc {
      println!("pc mismatch! ref={:#x}, dut={:#x}", ref_event.pc, dut.pc);
      mismatch = true;
    }

    //check csr
    for i in 0..18 {
      if ref_event.csr[i] != dut.csr[i] {
        println!(
          "csr{} mismatch! ref={:#x}, dut={:#x}",
          csr_name(i),
          ref_event.csr[i],
          dut.csr[i]
        );
        mismatch = true;
      }
    }

    if mismatch {
      println!("dut display:");
      println!("pc:{:#x}", dut.pc);
      println!("inst:{:#x}", dut.inst);
      for i in 0..32 {
        println!("gpr{}:{:#x}", i, dut.gpr[i]);
      }
      for i in 0..18 {
        println!("csr{}:{:#x}", csr_name(i), dut.csr[i]);
      }

      println!("ref display:");
      self.refmodule.display();
      panic!("difftest mismatch!");
      //self.state = SimState::Finished;
    }
  }
}

#[cfg(feature = "trace")]
pub struct DumpControl {
  svscope: svdpi::SvScope,
  wave_path: String,
  dump_start: u64,
  dump_end: u64,

  dump_startd: bool,
}

#[cfg(feature = "trace")]
impl DumpControl {
  fn new(svscope: SvScope, wave_path: &str, dump_start: u64, dump_end: u64) -> Self {
    Self {
      svscope,
      wave_path: wave_path.to_owned(),
      dump_start,
      dump_end,

      dump_startd: false,
    }
  }

  pub fn start(&mut self) {
    if !self.dump_startd {
      dpi::dump_wave(self.svscope, &self.wave_path);
      self.dump_startd = true;
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
