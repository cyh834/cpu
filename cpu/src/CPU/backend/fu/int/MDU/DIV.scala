package cpu.backend.fu

import chisel3._
import chisel3.util._
import chisel3.stage._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import cpu._
import utility._

// TODO: 更好的实现
class DIVInterface(parameter: CPUParameter) extends Bundle {
  val XLEN = parameter.XLEN

  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val flush = Input(Bool())
  val in = Flipped(Decoupled(Vec(2, Input(UInt(XLEN.W)))))
  val signed = Input(Bool())
  val out = ValidIO(Output(UInt((XLEN*2).W)))
}

@instantiable
class DIV(val parameter: CPUParameter)
    extends FixedIORawModule(new DIVInterface(parameter))
    with SerializableModule[CPUParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val XLEN = parameter.XLEN


  // 有符号负数需要变成绝对值
  def abs(a: UInt, sign: Bool): (Bool, UInt) = {
    val s = a(XLEN - 1) && sign
    (s, Mux(s, -a, a))
  }

  val (aSign, aVal) = abs(io.in.bits(0), io.signed)
  val (bSign, bVal) = abs(io.in.bits(1), io.signed)

  val dividend = RegInit(0.U((XLEN*2).W))
  val divisor = RegInit(0.U((XLEN+1).W))
  val quotient = RegInit(0.U(XLEN.W))
  val remainder = RegInit(0.U(XLEN.W))

  val quotient_sign = RegInit(false.B)
  val remainder_sign = RegInit(false.B)

  val shift_count = RegInit(XLEN.U((log2Ceil(XLEN) + 1).W))
  val is_dividing = RegInit(false.B)
  val out_valid = RegInit(false.B)

  val sub = Wire(UInt((XLEN+1).W))
  sub := dividend(2 * XLEN - 1, XLEN - 1) - divisor

  when(io.flush) {
    is_dividing := false.B
    shift_count := XLEN.U
    out_valid := false.B
  }.elsewhen(io.in.valid && !is_dividing) {
    is_dividing := true.B
    dividend := Cat(0.U(XLEN.W), aVal)
    divisor := Cat(0.U(1.W), bVal)
    quotient := 0.U
    remainder := 0.U
    shift_count := XLEN.U
    quotient_sign := aSign ^ bSign
    remainder_sign := aSign
    out_valid := false.B
  }.elsewhen(is_dividing) {
    when(shift_count === 0.U) {
      is_dividing := false.B
      quotient := Mux(quotient_sign, (~quotient).asUInt + 1.U, quotient)
      remainder := Mux(remainder_sign, (~dividend(2 * XLEN - 1, XLEN)).asUInt + 1.U, dividend(2 * XLEN - 1, XLEN))
      out_valid := true.B
    }.elsewhen(sub(XLEN).asBool) {
      dividend := Cat(dividend((XLEN*2) - 2, 0), 0.U(1.W))
      quotient := Cat(quotient(XLEN - 2, 0), 0.U(1.W))
    }.otherwise {
      quotient := Cat(quotient(XLEN - 2, 0), 0.U(1.W)) | 1.U
      dividend := Cat(Cat(sub(XLEN - 1, 0), dividend(XLEN - 2, 0)), 0.U(1.W))
    }
    shift_count := shift_count - 1.U
  }.otherwise {
    out_valid := false.B
  }

  io.in.ready := !is_dividing
  io.out.valid := out_valid
  io.out.bits := Cat(quotient, remainder)
}

