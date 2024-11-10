package cpu.backend

import chisel3._
import chisel3.util._
import chisel3.probe.{define, Probe, ProbeValue}

import utility._
import cpu._

object ScoreBoardParameter {
  implicit def rwP: upickle.default.ReadWriter[ScoreBoardParameter] =
    upickle.default.macroRW
}

case class ScoreBoardParameter(addrWidth: UInt, dataWidth: UInt) 
    extends SerializableModuleParameter{
        numReadPorts = 2
        numWritePorts = 1
    }

class SB_ISU(parameter: ScoreBoardParameter) extends Bundle {
    val lookidx=Input(Vec(parameter.numReadPorts,UInt(parameter.addrWidth.W)))
    val isBusy=Output(Vec(parameter.numReadPorts, Bool()))

    val setidx=Input(Vec(parameter.numWritePorts, UInt(parameter.addrWidth.W)))
}

class SB_WB(parameter: ScoreBoardParameter) extends Bundle {
    val clearidx=Input(UInt(parameter.addrWidth.W))
}

class ScoreBoardInterface(parameter: ScoreBoardParameter) extends Bundle {
    val isu = new SB_ISU(parameter)
    val wb = new SB_WB(parameter)
}

class ScoreBoard(val parameter: ScoreBoardParameter)
    extends FixedIORawModule(new ScoreBoardInterface(parameter))
    with SerializableModule[ScoreBoardParameter] {

    val busy=RegInit(0.U(parameter.addrWidth.W))
    def mask(idx: UInt) = (1.U(parameter.addrWidth.W) << idx)(parameter.addrWidth - 1, 0)
    busy := Cat(((busy & ~(mask(io.wb.clearidx))) | (mask(io.isu.setidx)))(parameter.addrWidth - 1, 1), 0.U(1.W))
    //io.isu.isBusy :=  io.isu.lookidx.map(busy(_)).reduce(_ | _)
    for(i <- 0 until numSrc)
        io.isu.isBusy(i) := busy(io.isu.lookidx(i))
}