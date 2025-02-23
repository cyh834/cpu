package cpu.backend

import chisel3._
import chisel3.util._
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}

import utility._
import cpu._

class WbuInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val in = Flipped(DecoupledIO(new WriteBackIO(parameter)))
  val rfwrite = Vec(parameter.regfileParameter.numWritePorts, Flipped(new RfWritePort(parameter.regfileParameter)))
  // val scoreboard = Flipped(new SB_WB(parameter.scoreboardParameter))
  val redirect = new RedirectIO(parameter.VAddrBits)
  // val flush = Input(Bool())
}

@instantiable
class WBU(val parameter: CPUParameter)
    extends FixedIORawModule(new WbuInterface(parameter))
    with SerializableModule[CPUParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset
  io.in.ready := true.B

  // gpr
  io.rfwrite(0) <> io.in.bits.wb
  io.rfwrite(0).wen := io.in.bits.wb.wen & io.in.valid

  // scoreboard
  //io.scoreboard.clearidx := Mux(io.in.fire, io.in.bits.wb.addr, 0.U)

  // redirect
  io.redirect := io.in.bits.redirect
  io.redirect.valid := io.in.bits.redirect.valid && io.in.valid
}
