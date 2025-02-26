package cpu.backend.fu

import chisel3._
import chisel3.util._
import chisel3.stage._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import cpu._
import utility._

class MDUInterface(parameter: CPUParameter) extends Bundle {
    val XLEN = parameter.XLEN

    val clock = Input(Clock())
    val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
    val flush = Input(Bool())
    val in = Flipped(Decoupled(new Bundle{
        val src = Vec(2, Input(UInt(XLEN.W)))
        val func = Input(FuOpType())
    }))
    val out = ValidIO(new Bundle{
        val result = Output(UInt(XLEN.W))
    })
}

@instantiable
class MDU(val parameter: CPUParameter) 
    extends FixedIORawModule(new MDUInterface(parameter)) 
    with SerializableModule[CPUParameter]
    with ImplicitClock
    with ImplicitReset {
    override protected def implicitClock: Clock = io.clock
    override protected def implicitReset: Reset = io.reset

    val XLEN = parameter.XLEN

    val (src1, src2, func) = (io.in.bits.src(0), io.in.bits.src(1), io.in.bits.func)

    val isDiv = MDUOpType.isDiv(func)
    val isMul = MDUOpType.isMul(func)

    val isDivSign = MDUOpType.isDivSign(func)
    val isW = MDUOpType.isW(func)
    val isH = MDUOpType.isH(func)

    val mul = Instantiate(new MUL(parameter))
    val div = Instantiate(new DIV(parameter))
    mul.io.flush := io.flush
    div.io.flush := io.flush
    mul.io.clock := io.clock
    div.io.clock := io.clock
    mul.io.reset := io.reset
    div.io.reset := io.reset

    val signext = SignExt(_: UInt, parameter.XLEN+1)
    val zeroext = ZeroExt(_: UInt, parameter.XLEN+1)
    val mulInputFuncTable = List(
        MDUOpType.mul    -> (zeroext, zeroext),
        MDUOpType.mulh   -> (signext, signext),
        MDUOpType.mulhsu -> (signext, zeroext),
        MDUOpType.mulhu  -> (zeroext, zeroext)
    )

    mul.io.in.bits(0) := MuxLookup(MDUOpType.getMulOp(func), 0.U)(mulInputFuncTable.map(p => (MDUOpType.getMulOp(p._1), p._2._1(src1))))
    mul.io.in.bits(1) := MuxLookup(MDUOpType.getMulOp(func), 0.U)(mulInputFuncTable.map(p => (MDUOpType.getMulOp(p._1), p._2._2(src2))))

    val divInputFunc = (x: UInt) => Mux(isW, Mux(isDivSign, SignExt(x(31,0), XLEN), ZeroExt(x(31,0), XLEN)), x)
    div.io.in.bits(0) := divInputFunc(src1)
    div.io.in.bits(1) := divInputFunc(src2)
    div.io.signed := isDivSign

    mul.io.in.valid := io.in.valid & isMul
    div.io.in.valid := io.in.valid & isDiv

    val res_1 = Mux(isDiv, div.io.out.bits, mul.io.out.bits)
    val res_2 = Mux(isH, res_1(2*XLEN-1,XLEN), res_1(XLEN-1,0))
    val res = Mux(isW, SignExt(res_2(31,0),XLEN), res_2)
    io.out.bits.result := res

    io.in.ready := mul.io.in.ready & div.io.in.ready
    io.out.valid := mul.io.out.valid | div.io.out.valid
}
