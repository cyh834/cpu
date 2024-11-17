mod mem;

use mem::*;
use tracing::{debug, error, trace};

struct ShadowBusDevice {
  base: usize,
  size: usize,
  device: Box<dyn ShadowDevice>,
}

const MAX_DEVICES: usize = 3;

pub(crate) struct ShadowBus {
  devices: [ShadowBusDevice; MAX_DEVICES],
}

impl ShadowBus {
  /// Initiate the devices on the bus as specified in `tests/t1.ld`
  /// NOTE: For some reason DDR is not aligned in the address space
  pub fn new() -> Self {
    const DDR_SIZE: usize = 0x80000000;
    const SCALAR_SIZE: usize = 0x20000000;
    const SRAM_SIZE: usize = 0x00400000;

    Self {
      devices: [
        ShadowBusDevice {
          base: 0x20000000,
          size: SCALAR_SIZE,
          device: MemDevice::<SCALAR_SIZE>::new(),
        },
        ShadowBusDevice {
          base: 0x40000000,
          size: DDR_SIZE,
          device: MemDevice::<DDR_SIZE>::new(),
        },
        ShadowBusDevice {
          base: 0xc0000000,
          size: SRAM_SIZE,
          device: MemDevice::<SRAM_SIZE>::new(),
        },
      ],
    }
  }

  pub fn read_mem_axi(&self, addr: u32, size: u32, bus_size: u32) -> Vec<u8> {
    assert!(
      addr % size == 0 && bus_size % size == 0,
      "unaligned access addr={addr:#x} size={size}B dlen={bus_size}B"
    );

    let start = addr as usize;
    let end = (addr + size) as usize;

    let handler = self.devices.iter().find(|d| match d {
      ShadowBusDevice { base, size, device: _ } => *base <= start && end <= (*base + *size),
    });

    match handler {
      Some(ShadowBusDevice { base, size: _, device }) => {
        let offset = start - *base;
        let data = device.read_mem(offset, size as usize);

        if size < bus_size {
          let mut data_padded = vec![0; bus_size as usize];
          let start = (addr % bus_size) as usize;
          let end = start + data.len();
          data_padded[start..end].copy_from_slice(&data);

          data_padded
        } else {
          data
        }
      }
      None => {
        panic!("read addr={addr:#x} size={size}B dlen={bus_size}B leads to nowhere!");
        vec![0; bus_size as usize]
      }
    }
  }

  // size: 1 << awsize
  // bus_size: AXI bus width in bytes
  // masks: write strobes, len=bus_size
  // data: write data, len=bus_size
  pub fn write_mem_axi(
    &mut self,
    addr: u32,
    size: u32,
    bus_size: u32,
    masks: &[bool],
    data: &[u8],
  ) {
    assert!(
      addr % size == 0 && bus_size % size == 0,
      "unaligned write access addr={addr:#x} size={size}B dlen={bus_size}B"
    );

    if !masks.iter().any(|x| *x) {
      trace!("Mask 0 write detected");
      return;
    }

    let start = (addr & ((!bus_size) + 1)) as usize;
    let end = start + bus_size as usize;

    let handler = self.devices.iter_mut().find(|d| match d {
      ShadowBusDevice { base, size, device: _ } => *base <= start && end <= (*base + *size),
    });

    match handler {
      Some(ShadowBusDevice { base, size: _, device }) => {
        let offset = start - *base;
        device.write_mem_chunk(offset, bus_size as usize, Option::from(masks), data);
      }
      None => {
        panic!("write addr={addr:#x} size={size}B dlen={bus_size}B leads to nowhere!");
      }
    }
  }

  pub fn load_mem_seg(&mut self, vaddr: usize, data: &[u8]) {
    let handler = self
      .devices
      .iter_mut()
      .find(|d| match d {
        ShadowBusDevice { base, size, device: _ } => {
          *base <= vaddr as usize && (vaddr as usize + data.len()) <= (*base + *size)
        }
      })
      .unwrap_or_else(|| {
        panic!(
          "fail reading ELF into mem with vaddr={:#x}, len={}B: load memory to nowhere",
          vaddr,
          data.len()
        )
      });

    let offset = vaddr - handler.base;
    handler.device.write_mem_chunk(offset, data.len(), None, data)
  }
}




//use std::fs::File;
//use std::io::Read;
//use std::path::Path;
//use xmas_elf::program::{ProgramHeader, Type};
//use xmas_elf::{header, ElfFile};
//
//pub(crate) struct DEVICE{
//    pub(crate) mem: Mem,
//}
//
//impl DEVICE {
//    pub fn new() -> Self {
//        Self { mem: Mem::new() }
//    }
//
//    pub fn load_elf(&mut self, fname: &Path) -> anyhow::Result<u64> {
//      let mut file = File::open(fname).unwrap();
//      let mut buffer = Vec::new();
//      file.read_to_end(&mut buffer).unwrap();
//        
//      let elf_file = ElfFile::new(&buffer).unwrap();
//        
//      let header = elf_file.header;
//      assert_eq!(header.pt2.machine().as_machine(), header::Machine::RISC_V);
//      assert_eq!(header.pt1.class(), header::Class::ThirtyTwo);
//        
//      for ph in elf_file.program_iter() {
//        if let ProgramHeader::Ph32(ph) = ph {
//          if ph.get_type() == Ok(Type::Load) {
//            let offset = ph.offset as usize;
//            let size = ph.file_size as usize;
//            let addr = ph.virtual_addr as usize;
//        
//            let slice = &buffer[offset..offset + size];
//            #[cfg(feature = "difftest")]
//            super::nemu.memcpy(addr as u64, slice.as_ptr() as *mut _, size, DIFFTEST_TO_REF);
//            self.mem.write(addr, size, slice.to_vec()).unwrap();
//          }
//        }
//      }
//    
//      Ok(header.pt2.entry_point())
//    }
//}
//
//struct Mem {
//  mem: Vec<u8>,
//  size: usize,
//}
//
//pub const MEM_SIZE: usize = 1usize << 32;
//
//impl Mem {
//  pub fn new() -> Self {
//    Self { 
//        mem: vec![0; MEM_SIZE],
//        size: MEM_SIZE
//    }
//  }
//
//  pub fn read(&self, addr: u32, len: u32) -> &[u8] {
//    assert!(addr + len <= self.size);
//
//    let start = addr as usize;
//    let end = (addr + len) as usize;
//    &self.mem[start..end]
//  }
//
//  pub fn write(&mut self, addr: u32, len: u32, data: &[u8]) -> anyhow::Result<()> {
//    assert!(addr + len <= self.size);
//
//    let start = addr as usize;
//    let end = (addr + len) as usize;
//    let dst = &mut self.mem[start..end];
//    for (i, byte) in bytes.iter().enumerate() {
//      dst[i] = *byte;
//    } 
//
//    Ok(())
//  }
//}