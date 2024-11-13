package cpu.backend.fu

import chisel3._
import chisel3.util._
import chisel3.stage._
import cpu._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import utility._

class JMPInterface(parameter: CPUParameter) extends Bundle {
  val src = Vec(2, Input(UInt(parameter.XLEN.W)))
  val pc = Input(UInt(parameter.XLEN.W))
  val func = Input(FuOpType())
  val isRVC = Input(Bool())
  val result, target = Output(UInt(parameter.XLEN.W))
  val mistarget = Output(Bool())
  // val isAuipc = Output(Bool())
}

@instantiable
class JMP(val parameter: CPUParameter)
    extends FixedIORawModule(new JMPInterface(parameter))
    with SerializableModule[CPUParameter] {

  val (pc, func, isRVC) = (io.pc, io.func, io.isRVC)
  val XLEN = parameter.XLEN

  // val isJalr = JumpOpType.isJalr(func)
  val isAuipc = JumpOpType.isAuipc(func)

  val target = io.src.reduce(_ + _)
  val snpc = pc + Mux(isRVC, 2.U, 4.U)

  io.target := Cat(target(XLEN - 1, 1), false.B)
  io.result := Mux(isAuipc, target, snpc)
  io.mistarget := io.pc =/= io.target
  // io.isAuipc := isAuipc
}
