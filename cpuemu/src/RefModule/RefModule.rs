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
    self.module.regcpy(self.event.as_mut_ptr() as *mut (), DIFFTEST_TO_DUT);

    self.event
  }

  pub fn override_event(&mut self, event: NemuEvent) {
    self.module.regcpy(event.as_mut_ptr() as *mut (), DIFFTEST_TO_REF);
  }

  pub fn display(&self) {
    self.module.display();
  }
}
