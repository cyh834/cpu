
pub(crate) struct Nemu {
}

impl Nemu {
  pub fn new() -> Self {
    unsafe { difftest_init() }
    Nemu{}
  }

  pub fn regcpy(&self,dut: *mut (), direction: bool) {
    unsafe { difftest_regcpy(dut, direction) }
  }

  //pub fn csrcpy(&self,dut: *mut (), direction: bool) {
  //  unsafe { difftest_csrcpy(dut, direction) }
  //}

  pub fn memcpy(&self, nemu_addr: u64, dut_buf: *mut (), n: usize, direction: bool) {
    unsafe { difftest_memcpy(nemu_addr, dut_buf, n, direction) }
  }

  pub fn exec(&self, n: u64) {
    unsafe { difftest_exec(n) }
  }

  pub fn display(&self) {
    unsafe { difftest_display() }
  }

  pub fn raise_intr(&self, NO: u64) {
    unsafe { difftest_raise_intr(NO) }
  }

  pub fn load_flash(&self, flash_bin: *mut (), f_size: usize) {
    unsafe { difftest_load_flash(flash_bin, f_size) }
  }
}

extern "C" {
  pub fn difftest_init();
  pub fn difftest_regcpy(dut: *mut (), direction: bool);
  //pub fn difftest_csrcpy(dut: *mut (), direction: bool);
  pub fn difftest_memcpy(nemu_addr: u64, dut_buf: *mut (), n: usize, direction: bool);
  pub fn difftest_exec(n: u64);
  pub fn difftest_display();
  pub fn difftest_raise_intr(NO: u64);
  pub fn difftest_load_flash(flash_bin: *mut (), f_size: usize);
}

#[repr(C, packed)]
pub(crate) struct NemuEvent{
  pub gpr : [u64; 32],

  pub csr : [u64; 18],
  //pub mode : u64,
  //pub mstatus, sstatus : u64,
  //pub mepc, sepc : u64,
  //pub mtval, stval : u64,
  //pub mtvec, stvec : u64,
  //pub mcause, scause : u64,
  //pub satp : u64,
  //pub mip, mie : u64,
  //pub mscratch, sscratch : u64,
  //pub mideleg, medeleg : u64,
  pub pc : u64,
}

impl NemuEvent {
  pub fn new() -> Self {
    Self{
      gpr : [0; 32],
      csr : [0; 18],
      pc : 0,
    }
  }

  pub(crate) fn update(&mut self, dut_state: &NemuEvent) {
    super::REF.exec(1);

    let mut ref_state = NemuEvent::new();
    super::REF.regcpy(&ref_state, DIFFTEST_TO_DUT);
    assert_eq!(dut_state, ref_state);
  }
}

