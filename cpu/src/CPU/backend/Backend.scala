package cpu.backend

import chisel3._
import chisel3.util._
import chisel3.probe.{define, Probe, ProbeValue}

import utility._
import amba.axi4._
import cpu._
import cpu.frontend._

class BackendProbe(parameter: CPUParameter) extends Bundle {
  val instr = UInt(32.W)
  val pc = UInt(parameter.VAddrBits.W)
  val isRVC = Bool()
}

class BackendInterface(parameter: CPUParameter) extends Bundle {
  val in = Flipped(Decoupled(new DecodeIO))
  val dmem = new AXI4RWIrrevocable(parameter.dataMemoryParameter)
  val flush = Input(UInt(2.W))
  val probe = Output(Probe(new BackendProbe(parameter), layers.Verification))
}

@instantiable
class Backend(val parameter: CPUParameter)
    extends FixedIORawModule(new BackendInterface(parameter))
    with SerializableModule[CPUParameter] {

  val isu = Instantiate(new ISU(parameter))
  val exu = Instantiate(new EXU(parameter))
  val wbu = Instantiate(new WBU(parameter))

  PipelineConnect(isu.io.out, exu.io.in, exu.io.out.fire, io.flush(0))
  PipelineConnect(exu.io.out, wbu.io.in, true.B, io.flush(1))

  isu.io.in <> io.in
  isu.io.forward <> exu.io.forward

  isu.io.flush := io.flush(0)
  exu.io.flush := io.flush(1)

  val regfile = Instance(new RegFile(parameter.RegFileParameter))
  regfile.io.readPorts <> isu.io.rfread
  regfile.io.writePorts <> wbu.io.rfwrite

  val scoreboard = Instance(new ScoreBoard(parameter.ScoreBoardParameter))
  scoreboard.io.isu <> isu.io.scoreboard
  scoreboard.io.wb <> wbu.io.scoreboard

  io.dmem <> exu.io.dmem

  val probeWire: BackendProbe = Wire(new BackendProbe(parameter))
  define(io.out.probe, ProbeValue(probeWire))
  probeWire.instr := DontCare
  probeWire.pc := DontCare
  probeWire.isRVC := DontCare

  // io.redirect <> wbu.io.redirect
  // forward
  // isu.io.forward <> exu.io.forward

  // io.memMMU.imem <> exu.io.memMMU.imem
  // io.memMMU.dmem <> exu.io.memMMU.dmem
  // io.dmem <> exu.io.dmem
}
