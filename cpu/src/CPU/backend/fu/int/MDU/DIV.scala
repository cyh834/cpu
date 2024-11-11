package core.backend.fu

import chisel3._
import chisel3.util._
import chisel3.stage._

//todo: 更好的实现
class div extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool()) // 为高表示要取消除法（修改一下除法器状态就行）
    val in = Decoupled(new Bundle {
      val dividend = Input(UInt(64.W)) // 被除数
      val divisor = Input(UInt(64.W)) // 除数
      val divw = Input(Bool()) // 为高表示输入的是 32 位除法
      val div_signed = Input(Bool()) // 表示是不是有符号除法，为高表示是有符号除法
    })
    val out = ValidIO(new Bundle {
      val quotient = Output(UInt(64.W)) // 商
      val remainder = Output(UInt(64.W)) // 余数
    })
  })
  val divbits = Wire(UInt((6.W)))
  val divBits = RegInit(0.U(6.W))
  val dividend_abs = Wire(UInt((64 << 1).W))
  val divisor_abs = Wire(UInt((64 + 1).W))

  divbits := Mux(io.in.bits.divw, 31.U, 63.U)

  // 有符号负数需要取反加一得到绝对值
  dividend_abs := Mux(
    io.in.bits.div_signed && io.in.bits.dividend(divbits).asBool,
    Mux(
      divbits === 31.U,
      (~Cat(Fill(96, io.in.bits.dividend(31)), io.in.bits.dividend(31, 0))).asUInt + 1.U,
      (~Cat(Fill(64, io.in.bits.dividend(63)), io.in.bits.dividend(63, 0))).asUInt + 1.U
    ),
    io.in.bits.dividend
  )
  divisor_abs := Mux(
    io.in.bits.div_signed && io.in.bits.divisor(divbits).asBool,
    Mux(
      divbits === 31.U,
      (~Cat(Fill(33, io.in.bits.divisor(31)), io.in.bits.divisor(31, 0))).asUInt + 1.U,
      (~Cat(Fill(1, io.in.bits.divisor(63)), io.in.bits.divisor(63, 0))).asUInt + 1.U
    ),
    io.in.bits.divisor
  )

  val quotient = RegInit(0.U(64.W))
  val remainder = RegInit(0.U(64.W))
  val dividend = RegInit(0.U((64 << 1).W))
  val divisor = RegInit(0.U((64 + 1).W))

  val shift_count = RegInit((64).U((log2Ceil(64) + 1).W))

  val is_dividing = RegInit(false.B)
  val is_dividing_divw = RegInit(false.B)
  val is_dividing_signed = RegInit(false.B)

  val sub = Wire(UInt((64 + 1).W))

  val dividend_sign = io.in.bits.dividend(64 - 1) & is_dividing_signed
  val divisor_sign = io.in.bits.divisor(64 - 1) & is_dividing_signed
  val quotient_sign = RegInit(false.B)
  val remainder_sign = RegInit(false.B)
  val out_valid = RegInit(false.B)

  sub := dividend(2 * 64 - 1, 64 - 1) - divisor

  io.in.ready := !is_dividing

  when(io.flush) {
    is_dividing := false.B
    shift_count := 64.U
    out_valid := false.B
  }.elsewhen(io.in.valid && !is_dividing) {
    is_dividing := true.B
    is_dividing_divw := io.in.bits.divw
    is_dividing_signed := io.in.bits.div_signed
    divBits := divbits
    dividend := dividend_abs
    divisor := divisor_abs
    quotient := 0.U
    remainder := 0.U
    shift_count := (64).U
    quotient_sign := dividend_sign ^ divisor_sign
    remainder_sign := dividend_sign
    out_valid := false.B
  }.elsewhen(is_dividing) {
    when(shift_count === 0.U) {
      is_dividing := false.B

      quotient := Mux(quotient_sign, (~quotient).asUInt + 1.U, quotient)
      remainder := Mux(remainder_sign, (~dividend(2 * 64 - 1, 64)).asUInt + 1.U, dividend(2 * 64 - 1, 64))

      out_valid := true.B

    }.elsewhen(sub(64).asBool) {
      dividend := Cat(dividend((64 << 1) - 2, 0), 0.U(1.W))
      quotient := Cat(quotient(64 - 2, 0), 0.U(1.W))
    }.otherwise {
      quotient := Cat(quotient(64 - 2, 0), 0.U(1.W)) | 1.U
      dividend := Cat(Cat(sub(64 - 1, 0), dividend(64 - 2, 0)), 0.U(1.W))
    }

    shift_count := shift_count - 1.U

  }.otherwise {
    out_valid := false.B
  }

  // when(out_valid){
  //  printf("dividend: %x, divisor: %x, quotient: %x, remainder: %x, divw: %x, div_signed: %x\n", io.in.bits.dividend, io.in.bits.divisor, io.quotient, io.remainder,io.in.bits.divw,io.in.bits.div_signed)
  // }
  // printf("%d %x %x %x %x %x\n",shift_count,dividend,divisor,quotient,remainder,dividend(2*64-1, 64))

  io.out.valid := out_valid
  io.out.bits.quotient := Mux(is_dividing_divw, Cat(0.U(32.W), quotient(31, 0)), quotient)
  io.out.bits.remainder := Mux(is_dividing_divw, Cat(0.U(32.W), remainder(31, 0)), remainder)
}
