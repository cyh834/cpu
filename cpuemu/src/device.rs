use std::fs::File;
use std::io::Read;
use std::path::Path;
use xmas_elf::program::{ProgramHeader, Type};
use xmas_elf::{header, ElfFile};

pub(crate) struct DEVICE{
    pub(crate) mem: Mem,
}

impl DEVICE {
    pub fn new() -> Self {
        Self { mem: Mem::new() }
    }

    pub fn load_elf(&mut self, fname: &Path) -> anyhow::Result<u64> {
      let mut file = File::open(fname).unwrap();
      let mut buffer = Vec::new();
      file.read_to_end(&mut buffer).unwrap();
        
      let elf_file = ElfFile::new(&buffer).unwrap();
        
      let header = elf_file.header;
      assert_eq!(header.pt2.machine().as_machine(), header::Machine::RISC_V);
      assert_eq!(header.pt1.class(), header::Class::ThirtyTwo);
        
      for ph in elf_file.program_iter() {
        if let ProgramHeader::Ph32(ph) = ph {
          if ph.get_type() == Ok(Type::Load) {
            let offset = ph.offset as usize;
            let size = ph.file_size as usize;
            let addr = ph.virtual_addr as usize;
        
            let slice = &buffer[offset..offset + size];
            #[cfg(feature = "difftest")]
            super::nemu.memcpy(addr as u64, slice.as_ptr() as *mut _, size, DIFFTEST_TO_REF);
            self.mem.write(addr, size, slice.to_vec()).unwrap();
          }
        }
      }
    
      Ok(header.pt2.entry_point())
    }
}

struct Mem {
  mem: Vec<u8>,
  size: usize,
}

pub const MEM_SIZE: usize = 1usize << 32;

impl Mem {
  pub fn new() -> Self {
    Self { 
        mem: vec![0; MEM_SIZE],
        size: MEM_SIZE
    }
  }

  pub fn read(&self, addr: u32, len: u32) -> &[u8] {
    assert!(addr + len <= self.size);

    let start = addr as usize;
    let end = (addr + len) as usize;
    &self.mem[start..end]
  }

  pub fn write(&mut self, addr: u32, len: u32, data: &[u8]) -> anyhow::Result<()> {
    assert!(addr + len <= self.size);

    let start = addr as usize;
    let end = (addr + len) as usize;
    let dst = &mut self.mem[start..end];
    for (i, byte) in bytes.iter().enumerate() {
      dst[i] = *byte;
    } 

    Ok(())
  }
}