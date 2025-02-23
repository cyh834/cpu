mod mem;
use mem::*;

mod uart;
use uart::*;

use anyhow;
use tracing::{error, trace};

// 抽象设备
trait ShadowDevice: Send + Sync {
  fn new() -> Box<dyn ShadowDevice>
  where
    Self: Sized;
  /// addr: offset respect to the base of this device
  fn read_mem(&self, addr: usize, size: usize) -> Vec<u8>;
  /// addr: offset respect to the base of this device
  /// strobe: signals which element in data is valid, None = all valid
  fn write_mem_chunk(&mut self, addr: usize, size: usize, strobe: Option<&[bool]>, data: &[u8]);
}

struct ShadowBusDevice {
  base: usize,
  size: usize,
  device: Box<dyn ShadowDevice>,
}

// 所有设备
const MAX_DEVICES: usize = 2;
pub(crate) struct ShadowBus {
  devices: [ShadowBusDevice; MAX_DEVICES],
}

impl ShadowBus {
  /// Initiate the devices on the bus as specified in `nexus-am`
  pub fn new() -> Self {
    //const SCALAR_SIZE: usize = 0x20000000;
    //const DDR_SIZE: usize = 0x80000000;
    //const SRAM_SIZE: usize = 0x00400000;

    Self {
      devices: [
        ShadowBusDevice {
          base: 0x40600000,
          size: 0x10,
          device: Uart::<0x10>::new(),
        },
        ShadowBusDevice {
          base: 0x80000000,
          size: 0x08000000,
          device: MemDevice::<0x08000000>::new(),
        },
        //ShadowBusDevice {
        //  base: 0x20000000,
        //  size: SCALAR_SIZE,
        //  device: MemDevice::<SCALAR_SIZE>::new(),
        //},
        //ShadowBusDevice {
        //  base: 0x40000000,
        //  size: DDR_SIZE,
        //  device: MemDevice::<DDR_SIZE>::new(),
        //},
        //ShadowBusDevice {
        //  base: 0xc0000000,
        //  size: SRAM_SIZE,
        //  device: MemDevice::<SRAM_SIZE>::new(),
        //},
      ],
    }
  }

  pub fn read_mem_axi(&self, addr: u32, size: u32, bus_size: u32) -> anyhow::Result<Vec<u8>> {
    if addr % size != 0 || bus_size % size != 0 {
      return Ok(vec![0xde, 0xad, 0xbe, 0xef]);
      // anyhow::bail!("read_mem_axi addr={addr:#x} size={size}B dlen={bus_size}B");
    }

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

          Ok(data_padded)
        } else {
          Ok(data)
        }
      }
      None => {
        anyhow::bail!("read addr={addr:#x} size={size}B dlen={bus_size}B leads to nowhere!");
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
  ) -> anyhow::Result<()> {
    if addr % size != 0 || bus_size % size != 0 {
      anyhow::bail!("write_mem_axi addr={addr:#x} size={size}B dlen={bus_size}B");
    }

    if !masks.iter().any(|x| *x) {
      trace!("Mask 0 write detected");
      return Ok(());
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
        anyhow::bail!("write addr={addr:#x} size={size}B dlen={bus_size}B leads to nowhere!");
      }
    }
    Ok(())
  }

  pub fn load_mem_seg(&mut self, vaddr: usize, data: &[u8]) -> anyhow::Result<()> {
    let handler = self
      .devices
      .iter_mut()
      .find(|d| match d {
        ShadowBusDevice { base, size, device: _ } => {
          *base <= vaddr as usize && (vaddr as usize + data.len()) <= (*base + *size)
        }
      })
      .ok_or_else(|| {
        anyhow::anyhow!(
          "fail reading ELF into mem with vaddr={:#x}, len={}B: load memory to nowhere",
          vaddr,
          data.len()
        )
      })?;

    let offset = vaddr - handler.base;
    handler.device.write_mem_chunk(offset, data.len(), None, data);
    Ok(())
  }
}
