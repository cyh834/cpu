package cpu.backend.fu

import chisel3._
import chisel3.util._
import chisel3.stage._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import cpu._
import utility._

class DmemReq(vaddrBits: Int, dataBits: Int) extends Bundle {
  val addr = Input(UInt(vaddrBits.W))
  val data = Input(UInt(dataBits.W))
  val strb = Input(UInt((dataBits / 8).W))
  val size = Input(UInt(2.W))
  val isStore = Input(Bool())
}

class DmemResp(dataBits: Int) extends Bundle {
  val data = Output(UInt(dataBits.W))
}

class DmemInterface(vaddrBits: Int, dataBits: Int) extends Bundle {
  val req = Decoupled(new DmemReq(vaddrBits, dataBits))
  val resp = Flipped(Decoupled(new DmemResp(dataBits)))
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
  val dmem = new DmemInterface(parameter.VAddrBits, parameter.DataBits)
}

@instantiable
class LSU(val parameter: CPUParameter)
    extends FixedIORawModule(new LSUInterface(parameter))
    with SerializableModule[CPUParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val size = LSUOpType.size(io.func)
  val addr = io.src(0) + io.imm
  val offset = addr(2, 0) << 3.U

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
  // req
  io.dmem.req.bits.addr := addr
  io.dmem.req.bits.size := size
  io.dmem.req.bits.data := (io.src(1) << offset)(63, 0)
  io.dmem.req.bits.strb := mask(addr, size)
  io.dmem.req.valid := io.valid

  io.dmem.resp.ready := true.B

  // resp
  val load_data = (io.dmem.resp.bits.data >> offset)(63, 0)
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
  io.result := HoldUnless(result, io.dmem.resp.fire)

  io.out_valid := io.dmem.resp.fire
}
