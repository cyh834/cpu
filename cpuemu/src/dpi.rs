use std::cmp::max;
use std::ffi::{c_char, CString};
use std::sync::Mutex;

use crate::drive::Driver;
use crate::plusarg::PlusArgMatcher;
use crate::CpuArgs;
use num_bigint::BigUint;
use svdpi::sys::dpi::{svBitVecVal, svLogic};
use svdpi::SvScope;

pub type SvBitVecVal = u32;

// --------------------------
// preparing data structures
// --------------------------

static DPI_TARGET: Mutex<Option<Box<Driver>>> = Mutex::new(None);

#[repr(C, packed)]
pub(crate) struct RetireData {
  pub inst: u32,
  pub pc: u64,
  pub gpr: [u64; 32],
  //csr
  pub mode: u64,
  pub mstatus: u64,
  pub sstatus: u64,
  pub mepc: u64,
  pub sepc: u64,
  pub mtval: u64,
  pub stval: u64,
  pub mtvec: u64,
  pub stvec: u64,
  pub mcause: u64,
  pub scause: u64,
  pub satp: u64,
  pub mip: u64,
  pub mie: u64,
  pub mscratch: u64,
  pub sscratch: u64,
  pub mideleg: u64,
  pub medeleg: u64,

  pub skip: bool,
  pub is_rvc: bool,
  pub rfwen: bool,
  pub is_load: bool,
  pub is_store: bool,
}

//----------------------
// dpi functions
//----------------------

#[no_mangle]
unsafe extern "C" fn sim_init() {
  let plusargs = PlusArgMatcher::from_args();
  let args = SimArgs::from_plusargs(&plusargs);
  args.setup_logger().unwrap();
  let scope = SvScope::get_current().expect("failed to get scope in sim_init");

  let mut dpi_target = DPI_TARGET.lock().unwrap();
  assert!(dpi_target.is_none(), "sim_init should be called only once");

  let driver = Box::new(Driver::new(scope, &args));
  *dpi_target = Some(driver);
}

#[no_mangle]
unsafe extern "C" fn sim_final() {
  //TODO:
}

#[no_mangle]
unsafe extern "C" fn sim_watchdog(reason: *mut c_char) {
  let mut driver = DPI_TARGET.lock().unwrap();
  if let Some(driver) = driver.as_mut() {
    *reason = driver.watchdog() as c_char;
  }
}

#[no_mangle]
unsafe extern "C" fn retire_instruction(retire_src: *const SvBitVecVal) {
  let mut driver = DPI_TARGET.lock().unwrap();
  if let Some(driver) = driver.as_mut() {
    let retire = &*(retire_src as *const RetireData);
    driver.retire_instruction(retire);
  }
}

//#[no_mangle]
//unsafe extern "C" fn difftest_ArchIntRegState(gpr: *mut c_longlong) {
//    let mut driver = DPI_TARGET.lock().unwrap();
//    if let Some(driver) = driver.as_mut() {
//       driver.dut.setgpr(gpr);
//    }
//}

//#[no_mangle]
//unsafe extern "C" fn difftest_CSRState(
//    mode: u64,mstatus: u64,sstatus: u64,
//    mepc: u64,sepc: u64,mtval: u64,stval: u64,
//    mtvec: u64,stvec: u64,mcause: u64,scause: u64,
//    satp: u64,mip: u64,mie: u64,mscratch: u64,
//    sscratch: u64,mideleg: u64,medeleg: u64,
//) {
//    let mut driver = DPI_TARGET.lock().unwrap();
//    if let Some(driver) = driver.as_mut() {
//        driver.dut.setcsr(
//            mode,mstatus,sstatus,
//            mepc,sepc,mtval,stval,
//            mtvec,stvec,mcause,scause,
//            satp,mip,mie,mscratch,
//            sscratch,mideleg,medeleg
//        );
//    }
//}

//#[no_mangle]
//unsafe extern "C" fn difftest_InstrCommit (
//    skip: bool, is_rvc: bool, rfwen: bool,
//    pc: u64, instr: u32,
//    is_load: bool, is_store: bool,
//) {
//    let mut driver = DPI_TARGET.lock().unwrap();
//    if let Some(driver) = driver.as_mut() {
//        driver.instr_commit(
//            skip, is_rvc, rfwen,
//            pc, instr,
//            is_load, is_store
//        );
//    }
//}

//#[no_mangle]
//unsafe extern "C" fn difftest_TrapEvent (
//    hasTrap: bool, Trapcode: u32, pc: u64
//    cycleCnt: u64, instrCnt: u64,
//    hasWFI: bool
//) {
//    let mut driver = DPI_TARGET.lock().unwrap();
//    if let Some(driver) = driver.as_mut() {
//        driver.trap_event(
//            hasTrap, Trapcode, pc,
//            cycleCnt, instrCnt,
//            hasWFI
//        );
//    }
//}

//TODO: AXI

//--------------------------------
// import functions and wrappers
//--------------------------------

mod dpi_export {
  use std::ffi::c_char;
  extern "C" {
    #[cfg(feature = "trace")]
    /// `export "DPI-C" function dump_wave(input string file)`
    pub fn dump_wave(path: *const c_char);
  }
}

#[cfg(feature = "trace")]
pub(crate) fn dump_wave(scope: SvScope, path: &str) {
  use svdpi::set_scope;

  let path_cstring = CString::new(path).unwrap();

  set_scope(scope);
  unsafe {
    dpi_export::dump_wave(path_cstring.as_ptr());
  }
}
