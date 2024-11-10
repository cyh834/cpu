package cpu.backend

import chisel3._
import chisel3.util._
import chisel3.probe.{define, Probe, ProbeValue}

import utility._
import cpu._

object RegFileParameter {
  implicit def rwP: upickle.default.ReadWriter[RegFileParameter] =
    upickle.default.macroRW
}

case class RegFileParameter(addrWidth: UInt, dataWidth: UInt) 
    extends SerializableModuleParameter{
        numReadPorts = 2
        numWritePorts = 1
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
  val readPorts = Vec(parameter.numReadPorts, new RfReadPort(parameter))
  val writePorts = Vec(parameter.numWritePorts, new RfWritePort(parameter))
}

class RegFile(val parameter: RegFileParameter)
    extends FixedIORawModule(new RegFileInterface(parameter))
    with SerializableModule[RegFileParameter] {

    val gpr = RegInit(VecInit(Seq.fill(parameter.NRReg)(0.U(parameter.dataWidth.W)))) 

    for (i <- 0 until numReadPorts) {
        io.readPorts(i).data := gpr(io.readPorts(i).addr)
    }

    for(i <- 0 until numWritePorts) {
        when(io.writePorts(i).wen && io.writePorts(i).addr =/= 0.U) {
            gpr(io.writePorts(i).addr) := io.writePorts(i).data
        }
    }

    assert(gpr(0) === 0.U, "x0 must be hardwired to 0")

}