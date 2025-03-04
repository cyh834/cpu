pub mod nemu;
pub use nemu::*;
use std::ffi::c_int;
use libc::{pipe, dup, dup2, close, read};
use std::io::stdout;


pub struct RefModule {
  module: Nemu,
  event: NemuEvent,
}

impl RefModule {
  pub fn new() -> Self {
    let module = Nemu::new();
    RefModule { module, event: NemuEvent::new() }
  }

  pub fn load_mem_seg(&mut self, addr: usize, bytes: &[u8]) {
    self.module.memcpy(
      addr as u64,
      bytes.as_ptr() as *mut (),
      bytes.len(),
      DIFFTEST_TO_REF,
    );
    //self.module.load_bytes_to_mem(addr, bytes.len(), bytes.to_vec());
  }

  pub fn step(&mut self) -> NemuEvent {
    self.module.exec(1);
    self.module.regcpy(
      &mut self.event as *mut NemuEvent as *mut (),
      DIFFTEST_TO_DUT,
    );

    self.event
  }

  pub fn override_event(&mut self, mut event: NemuEvent) {
    self.module.regcpy(&mut event as *mut NemuEvent as *mut (), DIFFTEST_TO_REF);
  }

  pub fn display(&self) {
    self.module.display();
  }

  pub fn status(&self) -> String {
    // 创建管道
    let mut fds: [c_int; 2] = [0; 2];
    unsafe { pipe(fds.as_mut_ptr()) };
    
    // 保存原始stdout
    let stdout_fd = unsafe { dup(libc::STDOUT_FILENO) };
    
    // 重定向stdout到管道写端
    unsafe { dup2(fds[1], libc::STDOUT_FILENO) };
    unsafe { close(fds[1]) };

    // 调用C函数（此时printf输出会进入管道）
    self.display();
    
    // 恢复原始stdout
    unsafe { 
        dup2(stdout_fd, libc::STDOUT_FILENO);
        close(stdout_fd);
    };

    // 从管道读端读取数据
    let mut buffer = Vec::with_capacity(1024);
    loop {
        let mut chunk = [0u8; 256];
        let count = unsafe { 
            read(fds[0], chunk.as_mut_ptr() as *mut libc::c_void, chunk.len()) 
        };
        if count <= 0 {
            break;
        }
        buffer.extend_from_slice(&chunk[..count as usize]);
    }
    unsafe { close(fds[0]) };

    String::from_utf8_lossy(&buffer).into_owned()
  }
}
//map index to csr name
pub fn csr_name(idx: usize) -> &'static str {
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

pub fn gpr_name(idx: usize) -> &'static str {
  match idx {
    0 => "$0",
    1 => "ra",
    2 => "sp",
    3 => "gp",
    4 => "tp",
    5 => "t0",
    6 => "t1",
    7 => "t2",
    8 => "s0",
    9 => "s1",
    10 => "a0",
    11 => "a1",
    12 => "a2",
    13 => "a3",
    14 => "a4",
    15 => "a5",
    16 => "a6",
    17 => "a7",
    18 => "s2",
    19 => "s3",
    20 => "s4",
    21 => "s5",
    22 => "s6",
    23 => "s7",
    24 => "s8",
    25 => "s9",
    26 => "s10",
    27 => "s11",
    28 => "t3",
    29 => "t4",
    30 => "t5",
    31 => "t6",
    _ => unreachable!(),
  }
}
