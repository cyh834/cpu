package cpu.frontend

import chisel3._
import chisel3.util._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}

import cpu._
import fifo._

object IBUFParameter {
  implicit def rwP: upickle.default.ReadWriter[IBUFParameter] =
    upickle.default.macroRW
}

case class IBUFParameter(useAsyncReset: Boolean, vaddrBits: Int) extends SerializableModuleParameter {
  val IBUFDepth = 8
}

class IBUFInterface(parameter: IBUFParameter) extends Bundle {
  val clock = Input(Clock())
  val reset  = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val in = Flipped(Decoupled(new IFU2IBUF(parameter.vaddrBits)))
  val out = Decoupled(new IBUF2IDU(parameter.vaddrBits))
}

@instantiable
class IBUF(val parameter: IBUFParameter)
    extends FixedIORawModule(new IBUFInterface(parameter))
    with SerializableModule[IBUFParameter] 
    with ImplicitClock
    with ImplicitReset {
    override protected def implicitClock: Clock = io.clock
    override protected def implicitReset: Reset = io.reset

  val buf: Instance[SyncFIFO[IFU2IBUF]] = Instantiate(new SyncFIFO(new IFU2IBUF(parameter.vaddrBits), parameter.IBUFDepth, parameter.useAsyncReset))
  io.in.ready := !buf.io.full
  io.out.valid := !buf.io.empty
  io.out.bits := buf.io.data_out

  buf.io.rd_en := io.out.fire
  buf.io.wr_en := io.in.fire
  buf.io.data_in := io.in.bits
}
