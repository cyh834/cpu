package cpu.backend

import chisel3._
import chisel3.util._
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}

import utility._
import cpu._

object ScoreBoardParameter {
  implicit def rwP: upickle.default.ReadWriter[ScoreBoardParameter] =
    upickle.default.macroRW
}

case class ScoreBoardParameter(useAsyncReset: Boolean, regNum: Int, addrWidth: Int, dataWidth: Int, numSrc: Int)
    extends SerializableModuleParameter {
  val numReadPorts = 2
  val numWritePorts = 1

  require(numWritePorts == 1, "Only support one write port")
}

class SB_ISU(parameter: ScoreBoardParameter) extends Bundle {
  val lookidx = Input(Vec(parameter.numReadPorts, UInt(parameter.addrWidth.W)))
  val isBusy = Output(Vec(parameter.numReadPorts, Bool()))

  val setidx = Input(Vec(parameter.numWritePorts, UInt(parameter.addrWidth.W)))
}

class SB_WB(parameter: ScoreBoardParameter) extends Bundle {
  val clearidx = Input(UInt(parameter.addrWidth.W))
}

class ScoreBoardInterface(parameter: ScoreBoardParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val flush = Input(Bool())
  val isu = new SB_ISU(parameter)
  val wb = new SB_WB(parameter)
}

@instantiable
class ScoreBoard(val parameter: ScoreBoardParameter)
    extends FixedIORawModule(new ScoreBoardInterface(parameter))
    with SerializableModule[ScoreBoardParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val busy = RegInit(0.U(parameter.regNum.W))
  def mask(idx: UInt) = (1.U(parameter.regNum.W) << idx)(parameter.regNum - 1, 0)
  busy := Cat(((busy & ~(mask(io.wb.clearidx))) | (mask(io.isu.setidx(0))))(parameter.regNum - 1, 1), 0.U(1.W))
  // io.isu.isBusy :=  io.isu.lookidx.map(busy(_)).reduce(_ | _)
  for (i <- 0 until parameter.numSrc)
    io.isu.isBusy(i) := busy(io.isu.lookidx(i))

  when(io.flush) {
    busy := 0.U(parameter.regNum.W)
  }
}
