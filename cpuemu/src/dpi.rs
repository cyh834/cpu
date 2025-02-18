#![allow(non_snake_case)]
#![allow(unused_variables)]

use std::ffi::{c_char, c_longlong};
//use std::cmp::max;
use std::sync::Mutex;
use tracing::{debug, trace};

use crate::drive::Driver;
use crate::drive::SimState;
use crate::plusarg::PlusArgMatcher;
use crate::SimArgs;
use svdpi::SvScope;
use tracing::{error, info};

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

unsafe fn load_from_payload<'a>(
  payload: *const SvBitVecVal,
  data_width: u32,
) -> (Vec<bool>, &'a [u8]) {
  let src = payload as *mut u8;
  let data_width_in_byte = (data_width / 8) as usize;
  let strb_width_in_byte = data_width_in_byte.div_ceil(8); // ceil divide by 8 to get byte width
  let payload_size_in_byte = strb_width_in_byte + data_width_in_byte; // data width in byte
  let byte_vec = std::slice::from_raw_parts(src, payload_size_in_byte);
  let strobe = &byte_vec[0..strb_width_in_byte];
  let data = &byte_vec[strb_width_in_byte..];

  let strb_width_in_bit = std::cmp::min(8, data_width_in_byte);
  let masks: Vec<bool> = strobe
    .into_iter()
    .flat_map(|strb| {
      let mask: Vec<bool> = (0..strb_width_in_bit).map(|i| (strb & (1 << i)) != 0).collect();
      mask
    })
    .collect();
  assert!(
    masks.len() == data.len(),
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

#[derive(Debug)]
pub struct RetireData {
    pub inst: u32,
    pub pc: u64,
    pub gpr: [u64; 32],
    pub csr: [u64; 18],
    pub skip: bool,
    pub is_rvc: bool,
    pub rfwen: bool,
    pub is_load: bool,
    pub is_store: bool
}

//----------------------
// dpi functions
//----------------------

pub(crate) static mut LAST_WRITE_PC: u64 = 0x0;
#[no_mangle]
unsafe extern "C" fn axi_write(
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
  if LAST_WRITE_PC != awaddr as u64 {
    trace!(
      "axi_write (channel_id={channel_id}, awid={awid}, awaddr={awaddr:#x}, \
      awlen={awlen}, awsize={awsize}, awburst={awburst}, awlock={awlock}, awcache={awcache}, \
      awprot={awprot}, awqos={awqos}, awregion={awregion})"
    );
  }
  let mut driver = DPI_TARGET.lock().unwrap();
  if let Some(driver) = driver.as_mut() {
    let (strobe, data) = load_from_payload(payload, driver.dlen);
    if let Err(e) = driver.axi_write(awaddr as u32, awsize as u64, &strobe, data) {
      if awaddr as u64 != LAST_WRITE_PC {
        error!("{}", e);
      }
      driver.state = SimState::BadTrap;
      LAST_WRITE_PC = awaddr as u64;
    }
  }
  LAST_WRITE_PC = awaddr as u64;
}

pub(crate) static mut LAST_READ_PC: u64 = 0;
#[no_mangle]
unsafe extern "C" fn axi_read(
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
  // 防止重复打印,但dirty且不靠谱
  if LAST_READ_PC != araddr as u64 {
    trace!(
      "axi_read (channel_id={channel_id}, arid={arid}, araddr={araddr:#x}, \
      arlen={arlen}, arsize={arsize}, arburst={arburst}, arlock={arlock}, arcache={arcache}, \
      arprot={arprot}, arqos={arqos}, arregion={arregion})"
    );
  }
  let mut driver = DPI_TARGET.lock().unwrap();
  if let Some(driver) = driver.as_mut() {
    let response = match driver.axi_read(araddr as u32, arsize as u64) {
      Ok(response) => response,
      Err(e) => {
        if araddr as u64 != LAST_READ_PC {
          error!("{}", e);
        }
        driver.state = SimState::BadTrap;
        LAST_READ_PC = araddr as u64;
        return;
      }
    };
    fill_axi_read_payload(payload, driver.dlen, &response);
  }
  LAST_READ_PC = araddr as u64;
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
  let mut driver = DPI_TARGET.lock().unwrap();
  if let Some(driver) = driver.as_mut() {
    match driver.state {
      SimState::GoodTrap => info!("sim_final: GoodTrap"),
      SimState::BadTrap => error!("sim_final: BadTrap"),
      SimState::Running => error!("sim_final: Running"),
      SimState::Timeout => info!("sim_final: Timeout"),
      SimState::Finished => {
          if driver.a0 == 0 {
              info!("sim_final: GoodTrap")
          } else {
              error!("sim_final: BadTrap")
          }
      },
    }
  }
}

#[no_mangle]
unsafe extern "C" fn sim_watchdog(reason: *mut c_char) {
  let mut driver = DPI_TARGET.lock().unwrap();
  if let Some(driver) = driver.as_mut() {
    *reason = driver.watchdog() as c_char;
  }
}

#[no_mangle]
unsafe extern "C" fn calculate_ipc(
  instruction_count: u64,
  simulation_time: u64,
) {
  let ipc = instruction_count as f64 / simulation_time as f64;
  info!("instruction_count: {}, simulation_time: {}, ipc: {}", instruction_count, simulation_time, ipc);
}

// 被内存布局搞晕了，先这样吧
#[no_mangle]
#[cfg(feature = "difftest")]
unsafe extern "C" fn retire_instruction(
  inst: u32,
  pc: u64,
  gpr0: u64,gpr1: u64, gpr2: u64,gpr3: u64,gpr4: u64,gpr5: u64,gpr6: u64,gpr7: u64,
  gpr8: u64,gpr9: u64,gpr10: u64,gpr11: u64,gpr12: u64,gpr13: u64,gpr14: u64,gpr15: u64,
  gpr16: u64,gpr17: u64,gpr18: u64,gpr19: u64,gpr20: u64,gpr21: u64,gpr22: u64,gpr23: u64,
  gpr24: u64,gpr25: u64,gpr26: u64,gpr27: u64,gpr28: u64,gpr29: u64,gpr30: u64,gpr31: u64,
  csr0: u64,csr1: u64, csr2: u64,csr3: u64,csr4: u64,csr5: u64,csr6: u64,csr7: u64,
  csr8: u64,csr9: u64,csr10: u64,csr11: u64,csr12: u64,csr13: u64,csr14: u64,csr15: u64,
  csr16: u64,csr17: u64,
  skip: bool,
  is_rvc: bool,
  rfwen: bool,
  is_load: bool,
  is_store: bool,
){
  let mut driver = DPI_TARGET.lock().unwrap();
  if let Some(driver) = driver.as_mut() {
    let gpr: [u64; 32] = [
      gpr0,gpr1,gpr2,gpr3,gpr4,gpr5,gpr6,gpr7,
      gpr8,gpr9,gpr10,gpr11,gpr12,gpr13,gpr14,gpr15,
      gpr16,gpr17,gpr18,gpr19,gpr20,gpr21,gpr22,gpr23,
      gpr24,gpr25,gpr26,gpr27,gpr28,gpr29,gpr30,gpr31
    ];
    let csr: [u64; 18] = [
      csr0,csr1,csr2,csr3,csr4,csr5,csr6,csr7,
      csr8,csr9,csr10,csr11,csr12,csr13,csr14,csr15,
      csr16,csr17
    ];
    let retire = RetireData{inst,pc,gpr,csr,skip,is_rvc,rfwen,is_load,is_store};
    trace!("{:x?}",retire);
    driver.retire_instruction(&retire);
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
  info!("start dump wave");
  unsafe {
    dpi_export::dump_wave(path_cstring.as_ptr());
  }
}
