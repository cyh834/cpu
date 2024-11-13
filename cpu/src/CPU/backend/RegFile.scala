package cpu.backend

import chisel3._
import chisel3.util._
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}

import utility._
import cpu._

object RegFileParameter {
  implicit def rwP: upickle.default.ReadWriter[RegFileParameter] =
    upickle.default.macroRW
}

case class RegFileParameter(useAsyncReset: Boolean, addrWidth: Int, dataWidth: Int, nrReg: Int) extends SerializableModuleParameter {
  val numReadPorts = 2
  val numWritePorts = 1
}

class RfReadPort(parameter: RegFileParameter) extends Bundle {
  val addr = Input(UInt(parameter.addrWidth.W))
  val data = Output(UInt(parameter.dataWidth.W))
}

class RfWritePort(parameter: RegFileParameter) extends Bundle {
  val wen = Input(Bool())
  val addr = Input(UInt(parameter.addrWidth.W))
  val data = Input(UInt(parameter.dataWidth.W))
}

class RegFileInterface(parameter: RegFileParameter) extends Bundle {
  val clock = Input(Clock())
  val reset  = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val readPorts = Vec(parameter.numReadPorts, new RfReadPort(parameter))
  val writePorts = Vec(parameter.numWritePorts, new RfWritePort(parameter))
}

@instantiable
class RegFile(val parameter: RegFileParameter)
    extends FixedIORawModule(new RegFileInterface(parameter))
    with SerializableModule[RegFileParameter] 
    with ImplicitClock
    with ImplicitReset {
    override protected def implicitClock: Clock = io.clock
    override protected def implicitReset: Reset = io.reset

  val gpr = RegInit(VecInit(Seq.fill(parameter.nrReg)(0.U(parameter.dataWidth.W))))

  for (i <- 0 until parameter.numReadPorts) {
    io.readPorts(i).data := gpr(io.readPorts(i).addr)
  }

  for (i <- 0 until parameter.numWritePorts) {
    when(io.writePorts(i).wen && io.writePorts(i).addr =/= 0.U) {
      gpr(io.writePorts(i).addr) := io.writePorts(i).data
    }
  }

  assert(gpr(0) === 0.U, "x0 must be hardwired to 0")

}
