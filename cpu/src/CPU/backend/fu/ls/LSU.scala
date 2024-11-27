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
  //val resp = Flipped(Decoupled(new StoreResp(parameter)))
}

class LSUInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  //execute
  val src = Vec(2, Input(UInt(parameter.XLEN.W)))
  val func = Input(FuOpType())
  val result = Output(UInt(parameter.XLEN.W))
  val isStore = Input(Bool())
  val valid = Input(Bool())
  //memory
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
  val offset = io.src(0)(2,0) << 3

  // load
  io.load.req.addr := io.src(0)
  io.load.req.size := size
  io.load.req.valid := !io.isStore && io.valid

  io.load.resp.ready := true.B

  // store
  io.store.req.addr := io.src(0)
  io.store.req.data := io.src(1)
  io.store.req.strb := Fill(1 << size, 1.U(1.W)) << io.src(0)(2,0)
  io.store.req.size := size
  io.store.req.valid := io.isStore && io.valid

  // result
  io.result := Mux(io.isStore, 0.U, io.load.resp.bits.data >> offset)
}