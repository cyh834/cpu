///// Spike RISC-V 模拟器的 Rust 接口实现
///// 该模块提供了与 Spike 模拟器交互的核心功能
//use libc::c_char;
//use std::ffi::{CStr, CString};
//use tracing::trace;
//
///// 从二进制数据中提取指定位范围的值
///// 
///// # 参数
///// * `binary` - 输入的32位二进制数
///// * `a` - 起始位（包含）
///// * `b` - 结束位（包含）
//pub fn clip(binary: u32, a: i32, b: i32) -> u32 {
//  assert!(a <= b, "a should be less than or equal to b");
//  let nbits = b - a + 1;
//  let mask = if nbits >= 32 {
//    u32::MAX
//  } else {
//    (1 << nbits) - 1
//  };
//  (binary >> a) & mask
//}
//
///// Spike 模拟器的主要结构体
///// 
///// # 字段
///// * `spike` - 底层 Spike 模拟器实例的指针
///// * `mem` - 模拟器的内存空间
///// * `size` - 内存大小
//pub struct Spike {
//  spike: *mut (),
//  pub mem: Vec<u8>,
//  pub size: usize,
//}
//
//unsafe impl Send for Spike {}
//
//// 默认的内存地址转换回调函数
//extern "C" fn default_addr_to_mem(target: *mut (), addr: u64) -> *mut u8 {
//  let spike = target as *mut Spike;
//  let addr = addr as usize;
//  unsafe {
//    let spike: &mut Spike = &mut *spike;
//    let ptr = spike.mem.as_mut_ptr().offset(addr as isize);
//    ptr
//  }
//}
//
//type FfiCallback = extern "C" fn(*mut (), u64) -> *mut u8;
//
//// 实现 Spike 结构体的方法
//impl Spike {
//  /// 创建新的 Spike 模拟器实例
//  /// 
//  /// # 参数
//  /// * `set` - ISA 扩展集合
//  /// * `lvl` - 特权级别
//  /// * `lane_number` - 向量处理器通道数
//  /// * `mem_size` - 内存大小
//  pub fn new(set: &str, lvl: &str, lane_number: usize, mem_size: usize) -> Box<Self> {
//    let set = CString::new(set).unwrap();
//    let lvl = CString::new(lvl).unwrap();
//    let spike = unsafe { spike_new(set.as_ptr(), lvl.as_ptr(), lane_number) };
//    let mut self_: Box<Spike> = Box::new(Spike { spike, mem: vec![0; mem_size], size: mem_size });
//
//    // TODO: support customized ffi
//    let ffi_target: *mut Spike = &mut *self_;
//    unsafe {
//      spike_register_callback(ffi_target as *mut (), default_addr_to_mem);
//    }
//
//    self_
//  }
//
//  pub fn get_proc(&self) -> Processor {
//    let processor = unsafe { spike_get_proc(self.spike) };
//    Processor { processor }
//  }
//
//  pub fn load_bytes_to_mem(
//    &mut self,
//    addr: usize,
//    len: usize,
//    bytes: Vec<u8>,
//  ) -> anyhow::Result<()> {
//    trace!("ld: addr: 0x{:x}, len: 0x{:x}", addr, len);
//    assert!(addr + len <= self.size);
//
//    let dst = &mut self.mem[addr..addr + len];
//    for (i, byte) in bytes.iter().enumerate() {
//      dst[i] = *byte;
//    }
//
//    Ok(())
//  }
//
//  pub fn mem_byte_on_addr(&self, addr: usize) -> anyhow::Result<u8> {
//    Ok(self.mem[addr])
//  }
//}
//
//impl Drop for Spike {
//  fn drop(&mut self) {
//    unsafe { spike_destruct(self.spike) }
//  }
//}
//
///// RISC-V 处理器的抽象
///// 提供访问处理器状态和执行指令的接口
//pub struct Processor {
//  processor: *mut (),
//}
//
//impl Processor {
//  /// 获取当前指令的反汇编字符串
//  pub fn disassemble(&self) -> String {
//    let bytes = unsafe { proc_disassemble(self.processor) };
//    let c_str = unsafe { CStr::from_ptr(bytes as *mut c_char) };
//    format!("{}", c_str.to_string_lossy())
//  }
//
//  pub fn reset(&self) {
//    unsafe { proc_reset(self.processor) }
//  }
//
//  pub fn get_state(&self) -> State {
//    let state = unsafe { proc_get_state(self.processor) };
//    State { state }
//  }
//
//  pub fn func(&self) -> u64 {
//    unsafe { proc_func(self.processor) }
//  }
//
//  pub fn get_insn(&self) -> u32 {
//    unsafe { proc_get_insn(self.processor) as u32 }
//  }
//
//  pub fn get_vreg_data(&self, idx: u32, offset: u32) -> u8 {
//    unsafe { proc_get_vreg_data(self.processor, idx, offset) }
//  }
//
//  pub fn get_rs1(&self) -> u32 {
//    unsafe { proc_get_rs1(self.processor) }
//  }
//
//  pub fn get_rs2(&self) -> u32 {
//    unsafe { proc_get_rs2(self.processor) }
//  }
//
//  pub fn get_rd(&self) -> u32 {
//    unsafe { proc_get_rd(self.processor) }
//  }
//
//  // vu
//  pub fn vu_get_vtype(&self) -> u32 {
//    unsafe { proc_vu_get_vtype(self.processor) as u32 }
//  }
//
//  pub fn vu_get_vxrm(&self) -> u32 {
//    unsafe { proc_vu_get_vxrm(self.processor) }
//  }
//
//  pub fn vu_get_vnf(&self) -> u32 {
//    unsafe { proc_vu_get_vnf(self.processor) }
//  }
//
//  pub fn vu_get_vill(&self) -> bool {
//    unsafe { proc_vu_get_vill(self.processor) }
//  }
//
//  pub fn vu_get_vxsat(&self) -> bool {
//    unsafe { proc_vu_get_vxsat(self.processor) }
//  }
//
//  pub fn vu_get_vl(&self) -> u32 {
//    unsafe { proc_vu_get_vl(self.processor) }
//  }
//
//  pub fn vu_get_vstart(&self) -> u16 {
//    unsafe { proc_vu_get_vstart(self.processor) }
//  }
//}
//
//impl Drop for Processor {
//  fn drop(&mut self) {
//    unsafe { proc_destruct(self.processor) }
//  }
//}
//
///// 处理器状态的抽象
///// 提供访问和修改处理器内部状态的接口
//pub struct State {
//  state: *mut (),
//}
//
//impl State {
//  pub fn set_pc(&self, pc: u64) {
//    unsafe { state_set_pc(self.state, pc) }
//  }
//
//  pub fn get_pc(&self) -> u64 {
//    unsafe { state_get_pc(self.state) }
//  }
//
//  pub fn handle_pc(&self, pc: u64) -> anyhow::Result<()> {
//    match unsafe { state_handle_pc(self.state, pc) } {
//      0 => Ok(()),
//      _ => Err(anyhow::anyhow!("Error handling pc")),
//    }
//  }
//
//  pub fn get_reg(&self, idx: u32, is_fp: bool) -> u32 {
//    unsafe { state_get_reg(self.state, idx, is_fp) }
//  }
//
//  pub fn get_reg_write_size(&self) -> u32 {
//    unsafe { state_get_reg_write_size(self.state) }
//  }
//
//  pub fn get_reg_write_index(&self, index: u32) -> u32 {
//    unsafe { state_get_reg_write_index(self.state, index) }
//  }
//
//  pub fn get_mem_write_size(&self) -> u32 {
//    unsafe { state_get_mem_write_size(self.state) }
//  }
//
//  pub fn get_mem_write(&self, index: u32) -> (u32, u64, u8) {
//    let addr = unsafe { state_get_mem_write_addr(self.state, index) };
//    let value = unsafe { state_get_mem_write_value(self.state, index) };
//    let size_by_byte = unsafe { state_get_mem_write_size_by_byte(self.state, index) };
//    (addr, value, size_by_byte)
//  }
//
//  pub fn get_mem_read_size(&self) -> u32 {
//    unsafe { state_get_mem_read_size(self.state) }
//  }
//
//  pub fn get_mem_read(&self, index: u32) -> (u32, u8) {
//    let addr = unsafe { state_get_mem_read_addr(self.state, index) };
//    let size_by_byte = unsafe { state_get_mem_read_size_by_byte(self.state, index) };
//    (addr, size_by_byte)
//  }
//
//  pub fn set_mcycle(&self, mcycle: usize) {
//    unsafe { state_set_mcycle(self.state, mcycle) }
//  }
//
//  pub fn clear(&self) {
//    unsafe { state_clear(self.state) }
//  }
//}
//
//impl Drop for State {
//  fn drop(&mut self) {
//    unsafe { state_destruct(self.state) }
//  }
//}
//
///// 外部 C 函数的声明
///// 这些函数由 Spike 模拟器的 C++ 实现提供
//#[link(name = "spike_interfaces")]
//extern "C" {
//  /// 注册内存访问回调函数
//  pub fn spike_register_callback(target: *mut (), callback: FfiCallback);
//  
//  /// 创建新的 Spike 实例
//  fn spike_new(set: *const c_char, lvl: *const c_char, lane_number: usize) -> *mut ();
//  
//  /// 获取 Spike 实例的处理器实例
//  fn spike_get_proc(spike: *mut ()) -> *mut ();
//  
//  /// 销毁 Spike 实例
//  fn spike_destruct(spike: *mut ());
//  
//  /// 反汇编处理器实例
//  fn proc_disassemble(proc: *mut ()) -> *mut c_char;
//  
//  /// 重置处理器实例
//  fn proc_reset(proc: *mut ());
//  
//  /// 获取处理器实例的状态
//  fn proc_get_state(proc: *mut ()) -> *mut ();
//  
//  fn proc_func(proc: *mut ()) -> u64;
//  
//  /// 获取处理器实例的指令
//  fn proc_get_insn(proc: *mut ()) -> u64;
//  
//  /// 获取处理器实例的寄存器数据
//  fn proc_get_vreg_data(proc: *mut (), vreg_idx: u32, vreg_offset: u32) -> u8;
//  
//  /// 获取处理器实例的寄存器 rs1
//  fn proc_get_rs1(proc: *mut ()) -> u32;
//  
//  /// 获取处理器实例的寄存器 rs2
//  fn proc_get_rs2(proc: *mut ()) -> u32;
//  
//  /// 获取处理器实例的寄存器 rd
//  fn proc_get_rd(proc: *mut ()) -> u32;
//  
//  /// 获取处理器实例的向量单元类型
//  fn proc_vu_get_vtype(proc: *mut ()) -> u64;
//  
//  /// 获取处理器实例的向量单元 xrm
//  fn proc_vu_get_vxrm(proc: *mut ()) -> u32;
//  
//  /// 获取处理器实例的向量单元 nf
//  fn proc_vu_get_vnf(proc: *mut ()) -> u32;
//  
//  /// 获取处理器实例的向量单元 vill
//  fn proc_vu_get_vill(proc: *mut ()) -> bool;
//  
//  /// 获取处理器实例的向量单元 vxsat
//  fn proc_vu_get_vxsat(proc: *mut ()) -> bool;
//  
//  /// 获取处理器实例的向量单元 vl
//  fn proc_vu_get_vl(proc: *mut ()) -> u32;
//  
//  /// 获取处理器实例的向量单元 vstart
//  fn proc_vu_get_vstart(proc: *mut ()) -> u16;
//  
//  /// 销毁处理器实例
//  fn proc_destruct(proc: *mut ());
//  
//  /// 设置处理器实例的状态 pc
//  fn state_set_pc(state: *mut (), pc: u64);
//  
//  /// 获取处理器实例的状态 pc
//  fn state_get_pc(state: *mut ()) -> u64;
//  
//  /// 获取处理器实例的状态寄存器
//  fn state_get_reg(state: *mut (), index: u32, is_fp: bool) -> u32;
//  
//  /// 获取处理器实例的状态寄存器写大小
//  fn state_get_reg_write_size(state: *mut ()) -> u32;
//  
//  /// 获取处理器实例的状态寄存器写索引
//  fn state_get_reg_write_index(state: *mut (), index: u32) -> u32;
//  
//  /// 获取处理器实例的状态内存写大小
//  fn state_get_mem_write_size(state: *mut ()) -> u32;
//  
//  /// 获取处理器实例的状态内存写地址
//  fn state_get_mem_write_addr(state: *mut (), index: u32) -> u32;
//  
//  /// 获取处理器实例的状态内存写值
//  fn state_get_mem_write_value(state: *mut (), index: u32) -> u64;
//  
//  /// 获取处理器实例的状态内存写大小字节
//  fn state_get_mem_write_size_by_byte(state: *mut (), index: u32) -> u8;
//  
//  /// 获取处理器实例的状态内存读大小
//  fn state_get_mem_read_size(state: *mut ()) -> u32;
//  
//  /// 获取处理器实例的状态内存读地址
//  fn state_get_mem_read_addr(state: *mut (), index: u32) -> u32;
//  
//  /// 获取处理器实例的状态内存读大小字节
//  fn state_get_mem_read_size_by_byte(state: *mut (), index: u32) -> u8;
//  
//  /// 处理处理器实例的状态 pc
//  fn state_handle_pc(state: *mut (), pc: u64) -> u64;
//  
//  /// 设置处理器实例的状态 mcycle
//  fn state_set_mcycle(state: *mut (), mcycle: usize);
//  
//  /// 清除处理器实例的状态
//  fn state_clear(state: *mut ());
//  
//  /// 销毁处理器实例的状态
//  fn state_destruct(state: *mut ());
//}