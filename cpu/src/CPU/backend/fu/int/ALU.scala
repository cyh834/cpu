package cpu.backend.fu

import chisel3._
import chisel3.util._
import chisel3.stage._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import cpu._
import utility._

class ALUInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val src = Vec(2, Input(UInt(parameter.XLEN.W)))
  val func = Input(FuOpType())
  val result = Output(UInt(parameter.XLEN.W))
}

@instantiable
class ALU(val parameter: CPUParameter)
    extends FixedIORawModule(new ALUInterface(parameter))
    with SerializableModule[CPUParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val (src1, src2, func) = (io.src(0), io.src(1), io.func)
  val XLEN = parameter.XLEN

  val subop = ALUOpType.isSubOp(func)
  val wop = ALUOpType.isWordOp(func)

  val adder_b = (src2 ^ Fill(XLEN, subop))
  val adder = src1 +& adder_b + subop

  val xor = src1 ^ src2
  val sltu = !adder(XLEN)
  val slt = xor(XLEN - 1) ^ sltu

  val shamt = if (XLEN == 64) Mux(wop, src2(4, 0), src2(5, 0)) else src2(4, 0)
  val ssrc1 = MuxLookup(func, src1)(
    Seq(
      ALUOpType.srlw -> ZeroExt(src1(31, 0), XLEN),
      ALUOpType.sraw -> SignExt(src1(31, 0), XLEN)
    )
  )

  val res = MuxLookup(func(4, 0), adder)(
    Seq(
      ALUOpType.sll -> (ssrc1 << shamt)(XLEN - 1, 0),
      ALUOpType.srl -> (ssrc1 >> shamt),
      ALUOpType.sra -> (ssrc1.asSInt >> shamt).asUInt,
      ALUOpType.sltu -> ZeroExt(sltu(0), XLEN),
      ALUOpType.slt -> ZeroExt(slt(0), XLEN),
      ALUOpType.and -> (src1 & src2),
      ALUOpType.or -> (src1 | src2),
      ALUOpType.xor -> xor
    )
  )

  io.result := (if (XLEN == 64) Mux(ALUOpType.isWordOp(func), SignExt(res(31, 0), 64), res) else res)

  // check
  when(func === ALUOpType.add) {
    assert(io.result === (src1 + src2)(XLEN - 1, 0))
  }.elsewhen(func === ALUOpType.sub) {
    assert(io.result === (src1 - src2)(XLEN - 1, 0))
  }.elsewhen(func === ALUOpType.sll) {
    assert(io.result === (src1 << src2(5, 0))(XLEN - 1, 0))
  }.elsewhen(func === ALUOpType.srl) {
    assert(io.result === (src1 >> src2(5, 0)))
  }.elsewhen(func === ALUOpType.sra) {
    assert(io.result === (src1.asSInt >> src2(5, 0)).asUInt)
  }.elsewhen(func === ALUOpType.and) {
    assert(io.result === (src1 & src2))
  }.elsewhen(func === ALUOpType.or) {
    assert(io.result === (src1 | src2))
  }.elsewhen(func === ALUOpType.xor) {
    assert(io.result === (src1 ^ src2))
  }.elsewhen(func === ALUOpType.sltu) {
    assert(io.result === (src1.asUInt < src2.asUInt).asUInt)
  }.elsewhen(func === ALUOpType.slt) {
    assert(io.result === (src1.asSInt < src2.asSInt).asUInt)
  }.elsewhen(func === ALUOpType.addw) {
    assert(io.result === SignExt((src1 + src2)(31, 0), 64))
  }.elsewhen(func === ALUOpType.subw) {
    assert(io.result === SignExt((src1 - src2)(31, 0), 64))
  }.elsewhen(func === ALUOpType.sllw) {
    assert(io.result === SignExt((src1 << src2(4, 0))(31, 0), 64))
  }.elsewhen(func === ALUOpType.srlw) {
    assert(io.result === SignExt((src1(31, 0) >> src2(4, 0))(31, 0), 64))
  }.elsewhen(func === ALUOpType.sraw) {
    assert(io.result === SignExt((src1(31, 0).asSInt >> src2(4, 0))(31, 0), 64))
  }.otherwise {
    assert(false.B, "ALU: unknown func %x", func)
  }

}
