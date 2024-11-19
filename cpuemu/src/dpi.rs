#![allow(non_snake_case)]
#![allow(unused_variables)]

use std::ffi::{c_char, c_longlong};
//use std::cmp::max;
use std::sync::Mutex;
use tracing::debug;

use crate::drive::Driver;
use crate::plusarg::PlusArgMatcher;
use crate::SimArgs;
use svdpi::{SvScope, get_time};

pub type SvBitVecVal = u32;

// --------------------------
// preparing data structures
// --------------------------

static DPI_TARGET: Mutex<Option<Box<Driver>>> = Mutex::new(None);

pub(crate) struct AxiReadPayload {
  pub(crate) data: Vec<u8>,
}

unsafe fn write_to_pointer(dst: *mut u8, data: &[u8]) {
  let dst = std::slice::from_raw_parts_mut(dst, data.len());
  dst.copy_from_slice(data);
}

unsafe fn fill_axi_read_payload(dst: *mut SvBitVecVal, dlen: u32, payload: &AxiReadPayload) {
  let data_len = 256 * (dlen / 8) as usize;
  assert!(payload.data.len() <= data_len);
  write_to_pointer(dst as *mut u8, &payload.data);
}

unsafe fn load_from_payload(
  payload: &*const SvBitVecVal,
  data_width: usize,
  size: usize,
) -> (Vec<bool>, &[u8]) {
  let src = *payload as *mut u8;
  let data_width_in_byte = std::cmp::max(size, 4);
  let strb_width_per_byte = if data_width < 64 { 4 } else { 8 };
  let strb_width_in_byte = size.div_ceil(strb_width_per_byte);

  let payload_size_in_byte = strb_width_in_byte + data_width_in_byte; // data width in byte
  let byte_vec = std::slice::from_raw_parts(src, payload_size_in_byte);
  let strobe = &byte_vec[0..strb_width_in_byte];
  let data = &byte_vec[strb_width_in_byte..];

  let masks: Vec<bool> = strobe
    .into_iter()
    .flat_map(|strb| {
      let mask: Vec<bool> = (0..strb_width_per_byte).map(|i| (strb & (1 << i)) != 0).collect();
      mask
    })
    .collect();
  assert_eq!(
    masks.len(),
    data.len(),
    "strobe bit width is not aligned with data byte width"
  );

  debug!(
    "load {payload_size_in_byte} byte from payload: raw_data={} strb={} data={}",
    hex::encode(byte_vec),
    hex::encode(strobe),
    hex::encode(data),
  );

  (masks, data)
}

#[repr(C, packed)]
pub(crate) struct RetireData {
  pub inst: u32,
  pub pc: u64,
  pub gpr: [u64; 32],
  pub csr: [u64; 18],

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
unsafe extern "C" fn axi_write_loadStoreAXI(
  channel_id: c_longlong,
  awid: c_longlong,
  awaddr: c_longlong,
  awlen: c_longlong,
  awsize: c_longlong,
  awburst: c_longlong,
  awlock: c_longlong,
  awcache: c_longlong,
  awprot: c_longlong,
  awqos: c_longlong,
  awregion: c_longlong,
  payload: *const SvBitVecVal,
) {
  debug!(
    "axi_write_loadStore (channel_id={channel_id}, awid={awid}, awaddr={awaddr:#x}, \
  awlen={awlen}, awsize={awsize}, awburst={awburst}, awlock={awlock}, awcache={awcache}, \
  awprot={awprot}, awqos={awqos}, awregion={awregion})"
  );
  let mut driver = DPI_TARGET.lock().unwrap();
  if let Some(driver) = driver.as_mut() {
    let data_width = if awsize <= 2 { 32 } else { 8 * (1 << awsize) } as usize;
    let (strobe, data) = load_from_payload(&payload, data_width, (driver.dlen / 8) as usize);
    driver.axi_write_load_store(awaddr as u32, awsize as u64, &strobe, data);
  }
}

#[no_mangle]
unsafe extern "C" fn axi_read_loadStoreAXI(
  channel_id: c_longlong,
  arid: c_longlong,
  araddr: c_longlong,
  arlen: c_longlong,
  arsize: c_longlong,
  arburst: c_longlong,
  arlock: c_longlong,
  arcache: c_longlong,
  arprot: c_longlong,
  arqos: c_longlong,
  arregion: c_longlong,
  payload: *mut SvBitVecVal,
) {
  // chisel use sync reset, registers are not reset at time=0
  // to avoid DPI trace (especially in verilator), we filter it here
  if svdpi::get_time() == 0 {
    debug!("axi_read_loadStoreAXI (ignored at time zero)");
    // TODO: better to fill zero to payload, but maintain the correct length for payload is too messy
    return;
  }

  debug!(
    "axi_read_loadStoreAXI (channel_id={channel_id}, arid={arid}, araddr={araddr:#x}, \
  arlen={arlen}, arsize={arsize}, arburst={arburst}, arlock={arlock}, arcache={arcache}, \
  arprot={arprot}, arqos={arqos}, arregion={arregion})"
  );
  let mut driver = DPI_TARGET.lock().unwrap();
  if let Some(driver) = driver.as_mut() {
    let response = driver.axi_read_load_store(araddr as u32, arsize as u64);
    fill_axi_read_payload(payload, driver.dlen, &response);
  }
}

#[no_mangle]
unsafe extern "C" fn axi_read_instructionFetchAXI(
  channel_id: c_longlong,
  arid: c_longlong,
  araddr: c_longlong,
  arlen: c_longlong,
  arsize: c_longlong,
  arburst: c_longlong,
  arlock: c_longlong,
  arcache: c_longlong,
  arprot: c_longlong,
  arqos: c_longlong,
  arregion: c_longlong,
  payload: *mut SvBitVecVal,
) {
  // chisel use sync reset, registers are not reset at time=0
  // to avoid DPI trace (especially in verilator), we filter it here
  if svdpi::get_time() == 0 {
    debug!("axi_read_instructionFetchAXI (ignored at time zero)");
    // TODO: better to fill zero to payload, but maintain the correct length for payload is too messy
    return;
  }

  debug!(
    "axi_read_instructionFetchAXI (channel_id={channel_id}, arid={arid}, araddr={araddr:#x}, \
  arlen={arlen}, arsize={arsize}, arburst={arburst}, arlock={arlock}, arcache={arcache}, \
  arprot={arprot}, arqos={arqos}, arregion={arregion})"
  );
  let mut driver = DPI_TARGET.lock().unwrap();
  if let Some(driver) = driver.as_mut() {
    let response = driver.axi_read_instruction_fetch(araddr as u32, arsize as u64);
    fill_axi_read_payload(payload, driver.dlen, &response);
  };
}

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
#[cfg(feature = "difftest")]
unsafe extern "C" fn retire_instruction(retire_src: *const SvBitVecVal) {
  let mut driver = DPI_TARGET.lock().unwrap();
  if let Some(driver) = driver.as_mut() {
    let retire = &*(retire_src as *const RetireData);
    driver.retire_instruction(retire);
  }
}

#[no_mangle]
unsafe extern "C" fn get_resetvector(resetvector: *mut c_longlong) {
  let mut driver = DPI_TARGET.lock().unwrap();
  if let Some(driver) = driver.as_mut() {
    *resetvector = driver.e_entry as c_longlong;
  }
}

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
  use std::ffi::CString;
  use svdpi::set_scope;

  let path_cstring = CString::new(path).unwrap();

  set_scope(scope);
  unsafe {
    dpi_export::dump_wave(path_cstring.as_ptr());
  }
}
