package cpu.backend

import chisel3._
import chisel3.util._
import chisel3.probe.{define, Probe, ProbeValue}

import utility._
import cpu._

class IsuInterface(parameter: CPUParameter) extends Bundle {
  val in = Flipped(Decoupled(new DecodeIO))
  val out = Decoupled(new DecodeIO)
  val flush = Input(Bool())
  val rfread = Flipped(new RfReadPort(parameter.RegFileParameter))
  val scoreboard = Flipped(new SB_ISU(parameter.ScoreBoardParameter))
  val forward = Flipped(new ForwardIO)
}

class ISU(val parameter: CPUParameter)
    extends FixedIORawModule(new IsuInterface(parameter))
    with SerializableModule[CPUParameter] {
  io.out <> io.in

  val busy = io.scoreboard.isBusy.reduce(_ | _)
  val canforward = Wire(Vec(parameter.numSrc, Bool()))
  for (i <- 0 until parameter.numSrc) {
    io.rfread.port(i).addr := io.in.bits.ctrl.lsrc(i)
    canforward(i) := io.scoreboard.isBusy(i) & io.forward.valid & (io.forward.rfDest === io.in.bits.ctrl.lsrc(i))
  }

  io.out.bits.data.src(0) := Mux(
    SrcType.isReg(io.in.bits.ctrl.srcType(0)),
    Mux(canforward(0), io.forward.rfData, io.rfread.port(0).data),
    io.in.bits.data.src(0)
  )
  io.out.bits.data.src(1) := Mux(
    SrcType.isReg(io.in.bits.ctrl.srcType(1)),
    Mux(canforward(1), io.forward.rfData, io.rfread.port(1).data),
    io.in.bits.data.src(1)
  )

  io.scoreboard.lookidx := io.in.bits.ctrl.lsrc

  io.out.valid := io.in.valid && (!busy | canforward.reduce(_ | _))
  io.in.ready := io.out.ready

  io.scoreboard.setidx := Mux(io.in.bits.ctrl.rfWen, io.in.bits.ctrl.ldest, 0.U)
}
