package cpu.backend

import chisel3._
import chisel3.util._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}

import utility._
import cpu._
import cpu.frontend._

class IsuInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val in = Flipped(Decoupled(new DecodeIO(parameter.iduParameter)))
  val out = Decoupled(new DecodeIO(parameter.iduParameter))
  val flush = Input(Bool())
  val rfread = Vec(parameter.regfileParameter.numReadPorts, Flipped(new RfReadPort(parameter.regfileParameter)))
  val scoreboard = Flipped(new SB_ISU(parameter.scoreboardParameter))
  val forward = Flipped(new ForwardIO(parameter.LogicRegsWidth, parameter.XLEN))
}

@instantiable
class ISU(val parameter: CPUParameter)
    extends FixedIORawModule(new IsuInterface(parameter))
    with SerializableModule[CPUParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset
  io.out :<>= io.in

  val busy = io.scoreboard.isBusy.reduce(_ | _)
  val canforward = Wire(Vec(parameter.NumSrc, Bool()))
  for (i <- 0 until parameter.NumSrc) {
    io.rfread(i).addr := io.in.bits.lsrc(i)
    canforward(i) := io.scoreboard.isBusy(i) & io.forward.valid & (io.forward.rfDest === io.in.bits.lsrc(i))
  }

  io.out.bits.src(0) := Mux(
    io.in.bits.srcIsReg(0),
    Mux(canforward(0), io.forward.rfData, io.rfread(0).data),
    io.in.bits.src(0)
  )
  io.out.bits.src(1) := Mux(
    io.in.bits.srcIsReg(1),
    Mux(canforward(1), io.forward.rfData, io.rfread(1).data),
    io.in.bits.src(1)
  )

  io.scoreboard.lookidx := io.in.bits.lsrc

  io.out.valid := io.in.valid && (!busy | canforward.reduce(_ | _))
  io.in.ready := io.out.ready

  io.scoreboard.setidx(0) := Mux(io.in.bits.rfWen, io.in.bits.ldest, 0.U)
}
