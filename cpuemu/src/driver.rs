use num_bigint::{BigUint, RandBigInt};
use num_traits::Zero;
use rand::Rng;
use tracing::{error, info, trace};

use crate::{
    dpi::{TestPayload, TestPayloadBits},
    CpuArgs,
    difftest::{REF, DUT},
    device::DEVICE,
};
use svdpi::{get_time, SvScope};

pub enum State {
    GoodTrap,
    BadTrap,
    Timeout,
    Running
}

const TRAP_PC: u64 = 0x12344321;

pub(crate) struct Driver {
    #[cfg(feature = "difftest")]
    ref: REF,

    dut: DUT,

    device: DEVICE,

    scope: SvScope,

    #[cfg(feature = "trace")]
    dump_control: DumpControl,

    pub(crate) data_width: u64,
    pub(crate) timeout: u64,
    pub(crate) clock_flip_time: u64,
    last_commit_cycle: u64,

    pub mut state: State,
}

impl Driver {
    fn get_tick(&self) -> u64 {
        get_time() / self.clock_flip_time
    }

    pub(crate) fn new(scope: SvScope, dut: DUT, args: &SimArgs) -> Self {
        let mut self_ = Self {
            #[cfg(feature = "difftest")]
            ref: REF::new(),
            dut: dut,
            device: DEVICE::new(),
            scope,
            #[cfg(feature = "trace")]
            dump_control: DumpControl::new(
              scope, 
              &args.wave_path, 
              args.dump_start, 
              args.dump_end
            ),
            data_width: env!("DESIGN_DATA_WIDTH").parse().unwrap(),
            timeout: env!("DESIGN_TIMEOUT").parse().unwrap(),
            clock_flip_time: env!("CLOCK_FLIP_TIME").parse().unwrap(),
            last_commit_cycle: 0,
        }
        self_.device.load_elf(&args.elf_file).unwrap();
        self_
    }


    pub(crate) fn watchdog(&mut self) -> u8 {
        if(self.state != State::Running) {
            return self.state;
        }

        let tick = self.get_tick();
        if tick - self.last_commit_cycle > self.timeout {
            error!("[{tick}] watchdog timeout (last_commit_cycle={self.last_input_cycle})");
            State::Timeout;
        } else {
            #[cfg(feature = "trace")]
            if self.dump_control.isend() {
                info!("[{tick}] run to dump end, exiting");
                return State::GoodTrap;
            }

            #[cfg(feature = "trace")]
            self.dump_control.try_start(tick);

            trace!("[{tick}] watchdog continue");
            State::Running
        }
    }

    #[cfg(feature = "difftest")]
    pub(crate) fn retire_instruction(&mut self, Data: &RetireData) {
      let mut dut_state = DUT::new();
      //TODO: too ugly
      dut_state.gpr = Data.gpr;
      dut_state.mode = Data.mode;
      dut_state.mstatus = Data.mstatus;
      dut_state.sstatus = Data.sstatus;
      dut_state.mepc = Data.mepc;
      dut_state.sepc = Data.sepc;
      dut_state.mtval = Data.mtval;
      dut_state.stval = Data.stval;
      dut_state.mtvec = Data.mtvec;
      dut_state.stvec = Data.stvec;
      dut_state.mcause = Data.mcause;
      dut_state.scause = Data.scause;
      dut_state.satp = Data.satp;
      dut_state.mip = Data.mip;
      dut_state.mie = Data.mie;
      dut_state.mscratch = Data.mscratch;
      dut_state.sscratch = Data.sscratch;
      dut_state.mideleg = Data.mideleg;
      dut_state.medeleg = Data.medeleg;
      dut_state.pc = Data.pc;
      self.dut.step_once(&dut_state, Data.skip, Data.is_rvc, Data.rfwen, Data.inst, Data.is_load, Data.is_store);

      if Data.pc == TRAP_PC {
        switch Data.gpr[10] {
          0 => {
            self.state = State::GoodTrap;
          }
          _ => {
            self.state = State::BadTrap;
          }
        }
      }
    }
    //pub(crate) fn instr_commit(
    //    &mut self,
    //    skip: bool,
    //    is_rvc: bool,
    //    rfwen: bool,
    //    pc: u64,
    //    instr: u32,
    //    is_load: bool,
    //    is_store: bool,
    //) {
    //  self.dut.setpc(pc);
    //  self.dut.step();
    //}

    //pub(crate) fn trap_event(
    //    &mut self,
    //    has_trap: bool,
    //    trap_code: u32,
    //    pc: u64,
    //    cycle_cnt: u64,
    //    instr_cnt: u64,
    //    has_wfi: bool,
    //) {
    //  if has_trap {
    //    if trap_code == 0 {
    //      trace!("Got a good trap \npc={pc} cycle_cnt={cycle_cnt} instr_cnt={instr_cnt}");
    //      self.state = State::GoodTrap;
    //    } else {
    //      trace!("Got a bad trap  \npc={pc} cycle_cnt={cycle_cnt} instr_cnt={instr_cnt}");
    //      self.state = State::BadTrap;
    //    }
    //  }
    //}
}

#[cfg(feature = "trace")]
pub struct DumpControl {
  svscope: svdpi::SvScope,
  wave_path: String,
  dump_start: u64,
  dump_end: u64,

  dump_startd: bool,
}

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
    if !self.dump_start {
      use crate::dpi::dump_wave;
      dump_wave(self.svscope, &self.wave_path);
      self.dump_started = true;
    }
  }

  pub fn isend() -> bool {
    self.dump_end != 0 && tick > self.dump_end
  }

  pub fn try_start(tick: u64) {
    if tick >= self.dump_start {
      self.start();
    }
  }
}