use super::ShadowDevice;

pub(super) struct Uart<const SIZE: usize> {
  regs: Box<[u8; SIZE]>,
  // 0x0: rxfifo
  // 0x4: txfifo
  // 0x8: stat
  // 0xc: ctrl
}

impl<const SIZE: usize> ShadowDevice for Uart<SIZE> {
  fn new() -> Box<dyn ShadowDevice>
  where
    Self: Sized,
  {
    Box::new(Self { regs: vec![0u8; SIZE].try_into().unwrap() })
  }

  fn read_mem(&self, addr: usize, size: usize) -> Vec<u8> {
    //TODO: 从键盘读取rxfifo
    let start = addr;
    let end = addr + size;
    self.regs[start..end].to_vec()
  }

  //fn write_mem(&mut self, addr: usize, data: u8) {
  //  self.mem[addr] = data;
  //}

  fn write_mem_chunk(&mut self, addr: usize, size: usize, strobe: Option<&[bool]>, data: &[u8]) {
    // NOTE: addr & size alignment check already done in ShadowBus, and ELF load can be unaligned anyway.
    if let Some(masks) = strobe {
      masks.iter().enumerate().for_each(|(i, mask)| {
        if *mask {
          self.regs[addr + i] = data[i];
        }
      })
    } else {
      let start = addr;
      let end = addr + size;
      self.regs[start..end].copy_from_slice(data);
    }

    //printf txfifo
    if self.regs[0x4] != 0x0 {
      print!("{}", self.regs[0x4] as char);
    }
    self.regs[0x4] = 0x0;
  }
}
