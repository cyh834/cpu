package cpu.backend

import chisel3._
import chisel3.util._
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}

import utility._
import amba.axi4._
import cpu._
import cpu.frontend._
import cpu.cache._

class BackendProbe(parameter: CPUParameter) extends Bundle {
  val retire: Valid[Retire] = Valid(new Retire)
}

class BackendInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val in = Flipped(Decoupled(new DecodeIO(parameter.iduParameter)))
  val dmem = AXI4RWIrrevocable(parameter.loadStoreAXIParameter)
  val bpuUpdate = Output(new BPUUpdate(parameter.bpuParameter))
  val flush = Input(UInt(2.W))
  val probe = Output(Probe(new BackendProbe(parameter), layers.Verification))
}

@instantiable
class Backend(val parameter: CPUParameter)
    extends FixedIORawModule(new BackendInterface(parameter))
    with SerializableModule[CPUParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val isu = Instantiate(new ISU(parameter))
  val exu = Instantiate(new EXU(parameter))
  val wbu = Instantiate(new WBU(parameter))

  PipelineConnect(isu.io.out, exu.io.in, exu.io.out.fire, io.flush(0))
  PipelineConnect(exu.io.out, wbu.io.in, true.B, io.flush(1))

  isu.io.in <> io.in
  isu.io.forward <> exu.io.forward

  isu.io.flush := io.flush(0)
  exu.io.flush := io.flush(1)

  exu.io.bpuUpdate <> io.bpuUpdate

  val regfile = Instantiate(new RegFile(parameter.regfileParameter))
  regfile.io.readPorts <> isu.io.rfread
  regfile.io.writePorts <> wbu.io.rfwrite

  val scoreboard = Instantiate(new ScoreBoard(parameter.scoreboardParameter))
  scoreboard.io.isu <> isu.io.scoreboard
  scoreboard.io.wb <> wbu.io.scoreboard

  val uncache = Instantiate(new DataUncache(parameter))
  uncache.io.flush := io.flush(0)
  uncache.io.load <> exu.io.load
  uncache.io.store <> exu.io.store
  io.dmem <> uncache.io.mem

  layer.block(layers.Verification) {
    val probeWire: BackendProbe = Wire(new BackendProbe(parameter))
    define(io.probe, ProbeValue(probeWire))
    probeWire.retire.valid := RegNext(wbu.io.rfwrite(0).wen)
    probeWire.retire.bits.inst := RegNext(wbu.io.in.bits.instr)
    probeWire.retire.bits.pc := RegNext(wbu.io.in.bits.pc)
    probeWire.retire.bits.gpr := probe.read(regfile.io.probe).gpr
    probeWire.retire.bits.csr := probe.read(exu.io.probe).csrprobe.csr
    probeWire.retire.bits.skip := false.B
    probeWire.retire.bits.is_rvc := false.B
    probeWire.retire.bits.rfwen := true.B
    probeWire.retire.bits.is_load := false.B
    probeWire.retire.bits.is_store := false.B
  }

  isu.io.clock := io.clock
  isu.io.reset := io.reset
  exu.io.clock := io.clock
  exu.io.reset := io.reset
  wbu.io.clock := io.clock
  wbu.io.reset := io.reset
  regfile.io.clock := io.clock
  regfile.io.reset := io.reset
  scoreboard.io.clock := io.clock
  scoreboard.io.reset := io.reset
  uncache.io.clock := io.clock
  uncache.io.reset := io.reset
  // io.redirect <> wbu.io.redirect
  // forward
  // isu.io.forward <> exu.io.forward

  // io.memMMU.imem <> exu.io.memMMU.imem
  // io.memMMU.dmem <> exu.io.memMMU.dmem
  // io.dmem <> exu.io.dmem
}
