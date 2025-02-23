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
  //val scoreboard = Flipped(new SB_ISU(parameter.scoreboardParameter))
  val forward = Flipped(new ForwardIO(parameter.LogicRegsWidth, parameter.XLEN))
  val wb = Vec(parameter.regfileParameter.numWritePorts, new RfWritePort(parameter.regfileParameter))
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

  def canForward_exu(i: Int) = io.forward.valid & (io.forward.rfDest === io.in.bits.lsrc(i))
  def canForward_wbu(i: Int) = io.wb(0).wen & (io.wb(0).addr === io.in.bits.lsrc(i))
  for(i <- 0 until parameter.NumSrc) {
    io.rfread(i).addr := io.in.bits.lsrc(i) // 从寄存器堆中读取
    io.out.bits.src(i) := MuxCase(io.rfread(i).data, Array(
                            !io.in.bits.srcIsReg(i) -> io.in.bits.src(i),
                            canForward_exu(i) -> io.forward.rfData,
                            canForward_wbu(i) -> io.wb(0).data
                          )) 
  }
  // val validVec = Wire(Vec(parameter.NumSrc, Bool()))
  // val canforward = Wire(Vec(parameter.NumSrc, Bool()))
  // for (i <- 0 until parameter.NumSrc) {
  //   io.rfread(i).addr := io.in.bits.lsrc(i)
  //   canforward(i) := io.forward.valid & (io.forward.rfDest === io.in.bits.lsrc(i))
  //   validVec(i) := !io.in.bits.srcIsReg(i) || !io.scoreboard.isBusy(i) || canforward(i) 
  //   io.out.bits.src(i) := Mux(
  //     io.in.bits.srcIsReg(i),
  //     Mux(canforward(i), io.forward.rfData, io.rfread(i).data),
  //     io.in.bits.src(i)
  //   )
  // }

  // io.scoreboard.lookidx := io.in.bits.lsrc

  // val valid = validVec.reduce(_ & _)
  // io.out.valid := io.in.valid && valid
  // io.in.ready := io.out.ready && valid

  // io.scoreboard.setidx(0) := Mux(io.out.bits.rfWen && !io.flush && io.out.fire, io.out.bits.ldest, 0.U)
}