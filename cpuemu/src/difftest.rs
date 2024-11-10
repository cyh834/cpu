enum{ DIFFTEST_TO_DUT, DIFFTEST_TO_REF };

pub(crate) struct REF

impl REF {
  pub fn new() -> Box<self> {
    unsafe { difftest_init() }
    self
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
pub(crate) struct DUT{
  pub gpr : [u64; 32],

  pub mode : u64,
  pub mstatus, sstatus : u64,
  pub mepc, sepc : u64,
  pub mtval, stval : u64,
  pub mtvec, stvec : u64,
  pub mcause, scause : u64,
  pub satp : u64,
  pub mip, mie : u64,
  pub mscratch, sscratch : u64,
  pub mideleg, medeleg : u64,
  pub pc : u64,
}

impl DUT {
  pub fn new() -> Self {
    Self
  }

  pub(crate) fn step_once(&mut self, dut_state: &DUT, skip, is_rvc, rfwen, inst, is_load, is_store) {
    super::REF.exec(1);

    let mut ref_state = DUT::new();
    super::REF.regcpy(&ref_state, DIFFTEST_TO_DUT);
    assert_eq!(dut_state, ref_state);
  }

  //pub fn setgpr(&mut self, gpr: *mut c_longlong) {
  //  self.gpr = gpr
  //}

  //pub fn setcsr(
  //  &mut self,
  //  mode: u64,mstatus: u64,sstatus: u64,
  //  mepc: u64,sepc: u64,mtval: u64,stval: u64,
  //  mtvec: u64,stvec: u64,mcause: u64,scause: u64,
  //  satp: u64,mip: u64,mie: u64,mscratch: u64,
  //  sscratch: u64,mideleg: u64,medeleg: u64,
  //) {
  //  self.mode = mode;
  //  self.mstatus = mstatus;
  //  self.sstatus = sstatus;
  //  self.mepc = mepc;
  //  self.sepc = sepc;
  //  self.mtval = mtval;
  //  self.stval = stval;
  //  self.mtvec = mtvec;
  //  self.stvec = stvec;
  //  self.mcause = mcause;
  //  self.scause = scause;
  //  self.satp = satp;
  //  self.mip = mip;
  //  self.mie = mie;
  //  self.mscratch = mscratch;
  //  self.sscratch = sscratch;
  //  self.mideleg = mideleg;
  //  self.medeleg = medeleg;
  //}

  //pub fn setpc(&mut self, pc: u64) {
  //  self.pc = pc;
  //}
}