pub use nemu::*;
enum{ DIFFTEST_TO_DUT, DIFFTEST_TO_REF };

pub struct RefModule {
  module: Nemu,
  event: NemuEvent,
}

impl RefModule {
  pub fn new() -> Self {
    let mut module = Nemu::new();
    RefModule{module, event: NemuEvent::new()}
  }

  pub fn load_mem_seg(&mut self, addr: usize, bytes: &[u8]) {
    self.module.memcpy(addr as u64, bytes.as_ptr() as *mut (), bytes.len(), DIFFTEST_TO_DUT);
    //self.module.load_bytes_to_mem(addr, bytes.len(), bytes.to_vec());
  }

  pub fn step(&mut self) -> NemuEvent {
    self.module.exec(1);
    self.module.regcpy(&mut self.event as *mut NemuEvent as *mut (), DIFFTEST_TO_DUT);

    self.event
  }

  pub fn override_event(&mut self, event: NemuEvent) {
    self.module.regcpy(event.as_mut_ptr() as *mut (), DIFFTEST_TO_REF);
  }

  pub fn display(&self) {
    self.module.display();
  }
}
//map index to csr name
pub(crate) fn csr_name(idx: usize) -> &'static str {
  match idx {
    0 => "mode",
    1 => "mstatus",
    2 => "sstatus",
    3 => "mepc",
    4 => "sepc",
    5 => "mtval",
    6 => "stval",
    7 => "mtvec",
    8 => "stvec",
    9 => "mcause",
    10 => "scause",
    11 => "satp",
    12 => "mip",
    13 => "mie",
    14 => "mscratch",
    15 => "sscratch",
    16 => "mideleg",
    17 => "medeleg",
    _ => unreachable!(),
  }
}
