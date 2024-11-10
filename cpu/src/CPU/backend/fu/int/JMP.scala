package core.backend.fu

import chisel3._
import chisel3.util._
import chisel3.stage._
import core._
import core.frontend.decode._
import utility._

class JMP extends CoreModule{
    val io = IO(new Bundle() {
        val src = Vec(numSrc, Input(UInt(XLEN.W)))
        val pc = Input(UInt(XLEN.W))
        val func = Input(FuOpType())
        val isRVC = Input(Bool())
        val result, target = Output(UInt(XLEN.W))
        val mistarget = Output(Bool())
        //val isAuipc = Output(Bool())
    })
    val (pc, func, isRVC) = (io.pc, io.func, io.isRVC)
    
    //val isJalr = JumpOpType.isJalr(func)
    val isAuipc = JumpOpType.isAuipc(func)

    val target = io.src.reduce(_ + _)
    val snpc = pc + Mux(isRVC, 2.U, 4.U)

    io.target := Cat(target(XLEN - 1, 1), false.B)
    io.result  := Mux(isAuipc, target, snpc)
    io.mistarget := io.pc =/= io.target
    //io.isAuipc := isAuipc
}