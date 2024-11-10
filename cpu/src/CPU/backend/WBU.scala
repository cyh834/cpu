package cpu.backend

import chisel3._
import chisel3.util._
import chisel3.probe.{define, Probe, ProbeValue}

import utility._
import cpu._

class WbuInterface(parameter: CPUParameter) extends Bundle{
  val in = Flipped(DecoupledIO(new WriteBackIO))
  val rfwrite = Flipped(new RfWritePort(parameter.RegFileParameter))
  val scoreboard = Flipped(new SB_WB(parameter.ScoreBoardParameter))
  val redirect = new RedirectIO
  //val flush = Input(Bool())
}

class WBU(val parameter: CPUParameter) 
  extends FixedIORawModule(new WbuInterface(parameter)) 
  with SerializableModule[CPUParameter]{
  io.rfwrite <> io.in.bits.wb
  io.rfwrite.port(0).wen := io.in.bits.wb.port(0).wen & io.in.valid
  io.in.ready := true.B

  io.scoreboard.clearidx := Mux(io.in.fire,io.in.bits.wb.port(0).addr,0.U)

  io.redirect.target := io.in.bits.redirect.target
  io.redirect.valid := io.in.bits.redirect.valid & io.in.valid
}