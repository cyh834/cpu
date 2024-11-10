//package core.backend.fu
//
//import chisel3._
//import chisel3.util._
//import chisel3.stage._
//import core.HasCoreParameter
//
//class MDU extends Module with HasCoreParameter{
//    val io=IO(new Bundle{
//        val src = Vec(2, Input(UInt(XLEN.W)))
//        val func = Input(FuOpType())
//        val result = Output(UInt(XLEN.W))
//        val flush = Input(Bool())
//    })
//    val (src1, src2, func) = (io.src(0), io.src(1), io.func)
//
//    val MUL = Module(new MUL)
//    val DIV = Module(new DIV)
//
//    mul.io.flush := io.flush
//    mul.io.in.multiplicand := src1
//    mul.io.in.multiplier := src2
//    mul.io.in.mul_signed := isSigned(func)
//    mul.io.in.mulw := MDUOpType.isW(func)
//
//}
