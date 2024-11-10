package cpu.frontend

import chisel3._
import chisel3.util._

import cpu._
import fifo._

object IBUParameter {
  implicit def rw: upickle.default.ReadWriter[IBUParameter] = 
    upickle.default.macroRW
}

case class IBUParameter(VAddrBits: Int) extends SerializableModuleParameter{
  val IBUFDepth = 8
}

class IBUFInterface(parameter: IBUParameter) extends Bundle{
  val in = Flipped(Decoupled(new IFU2IBUF(parameter.VAddrBits)))
  val out = Decoupled(new IBUF2IDU(parameter.VAddrBits))
}

class IBUF(parameter: IBUParameter)
  extends FixedIORawModule(new IBUFInterface(parameter))
  with SerializableModule[IBUParameter]{

  val buf: Instance[SyncFIFO[IFU2IBUF]] = Instantiate(new SyncFIFO(new IFU2IBUF, parameter.IBUFDepth))
  io.in.ready := !buf.io.full
  io.out.valid := !buf.io.empty
  io.out.bits := buf.io.data_out

  buf.io.rd_en := io.out.fire
  buf.io.wr_en := io.in.fire
  buf.io.data_in := io.in.bits
}