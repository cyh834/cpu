package cpu.backend.fu

import chisel3._
import chisel3.util._
import chisel3.stage._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import cpu._
import utility._

class LoadReq(parameter: CPUParameter) extends Bundle {
  val addr = Input(UInt(parameter.VAddrBits.W))
  val size = Input(UInt(2.W))
}

class LoadResp(parameter: CPUParameter) extends Bundle {
  val data = Output(UInt(parameter.DataBits.W))
}

class LoadInterface(parameter: CPUParameter) extends Bundle {
  val req = Decoupled(new LoadReq(parameter))
  val resp = Flipped(Decoupled(new LoadResp(parameter)))
}

class StoreReq(parameter: CPUParameter) extends Bundle {
  val addr = Input(UInt(parameter.VAddrBits.W))
  val data = Input(UInt(parameter.DataBits.W))
  val strb = Input(UInt(parameter.DataBytes.W))
  val size = Input(UInt(2.W))
}

//class StoreResp(parameter: CPUParameter) extends Bundle {
//}

class StoreInterface(parameter: CPUParameter) extends Bundle {
  val req = Decoupled(new StoreReq(parameter))
  // val resp = Flipped(Decoupled(new StoreResp(parameter)))
}

class LSUInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  // in
  val src = Vec(2, Input(UInt(parameter.XLEN.W)))
  val imm = Input(UInt(parameter.XLEN.W))
  val func = Input(FuOpType())
  val isStore = Input(Bool())
  val valid = Input(Bool())
  // out
  val result = Output(UInt(parameter.XLEN.W))
  val out_valid = Output(Bool())
  // memory
  val load = new LoadInterface(parameter)
  val store = new StoreInterface(parameter)
}

@instantiable
class LSU(parameter: CPUParameter)
    extends FixedIORawModule(new LSUInterface(parameter))
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val size = LSUOpType.size(io.func)
  val addr = io.src(0) + io.imm
  val offset = addr(2, 0) << 3.U

  // load
  io.load.req.bits.addr := addr
  io.load.req.bits.size := size
  io.load.req.valid := !io.isStore && io.valid

  io.load.resp.ready := true.B

  // store
  def mask(addr: UInt, size: UInt): UInt = {
    MuxLookup(size, 0xff.U)(
      Seq(
        "b00".U -> 0x1.U,
        "b01".U -> 0x3.U,
        "b10".U -> 0xf.U,
        "b11".U -> 0xff.U
      )
    ) << addr(2, 0)
  }
  io.store.req.bits.addr := addr
  io.store.req.bits.data := io.src(1) << offset
  io.store.req.bits.strb := mask(addr, size)
  io.store.req.bits.size := size
  io.store.req.valid := io.isStore && io.valid

  // load_result
  val load_data = io.load.resp.bits.data >> offset
  val sign_extend_data = MuxLookup(size, 0.U)(
    Seq(
      "b00".U -> SignExt(load_data(7, 0), parameter.XLEN),
      "b01".U -> SignExt(load_data(15, 0), parameter.XLEN),
      "b10".U -> SignExt(load_data(31, 0), parameter.XLEN),
      "b11".U -> load_data(63, 0)
    )
  )
  val zero_extend_data = MuxLookup(size, 0.U)(
    Seq(
      "b00".U -> ZeroExt(load_data(7, 0), parameter.XLEN),
      "b01".U -> ZeroExt(load_data(15, 0), parameter.XLEN),
      "b10".U -> ZeroExt(load_data(31, 0), parameter.XLEN),
      "b11".U -> load_data(63, 0)
    )
  )
  val result = Mux(LSUOpType.loadIsUnsigned(io.func), zero_extend_data, sign_extend_data)
  io.result := HoldUnless(result, io.load.resp.fire)

  io.out_valid := io.load.resp.fire || (io.isStore && io.store.req.ready)
}
