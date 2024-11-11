package core.backend.fu

import chisel3._
import chisel3.util._
import chisel3.stage._
import core._
import core.frontend.decode._
import utility._

class BRH extends CoreModule {
  val io = IO(new Bundle() {
    val src = Vec(2, Input(UInt(XLEN.W)))
    val func = Input(FuOpType())
    val pred_taken = Input(Bool())
    val taken, mispredict = Output(Bool())
    val pc = Input(UInt(XLEN.W))
    val offset = Input(UInt(XLEN.W))
    val target = Output(UInt(XLEN.W))
  })

  val (src1, src2, func) = (io.src(0), io.src(1), io.func)

  val sub = src1 - src2
  val sltu = !sub(XLEN - 1)
  val slt = src1(XLEN - 1) ^ src2(XLEN - 1) ^ sltu
  val xor = src1 ^ src2

  // 另外三条指令直接取反
  val taken = MuxLookup(BRUOpType.getBranchType(func), false.B)(
    Seq(
      BRUOpType.getBranchType(BRUOpType.beq) -> !xor.orR,
      BRUOpType.getBranchType(BRUOpType.blt) -> slt,
      BRUOpType.getBranchType(BRUOpType.bltu) -> sltu
    )
  ) ^ BRUOpType.isBranchInvert(func)

  io.taken := taken
  io.mispredict := io.pred_taken ^ taken
  io.target := io.pc + io.offset
}
