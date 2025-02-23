package cpu.frontend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.properties.{AnyClassType, Class, Property}
import org.chipsalliance.rvdecoderdb.Instruction

import cpu._
import utility._
import cpu.frontend.decoder._

object IDUParameter {
  implicit def rwP: upickle.default.ReadWriter[IDUParameter] =
    upickle.default.macroRW
}

case class IDUParameter(
  decoderParam:  DecoderParam,
  useAsyncReset: Boolean,
  addrBits:      Int,
  numSrc:        Int,
  regsWidth:     Int,
  xlen:          Int)
    extends SerializableModuleParameter

trait IDUBundle extends Bundle {
  val parameter: IDUParameter
  val addrBits = parameter.addrBits
  val regsWidth = parameter.regsWidth
  val numSrc = parameter.numSrc
  val xlen = parameter.xlen
}

class DecodeIO(val parameter: IDUParameter) extends IDUBundle {
  val srcIsReg = Vec(numSrc, Bool())
  val fuType = FuType()
  val fuOpType = FuOpType()
  val lsrc = Vec(numSrc, UInt(regsWidth.W))
  val ldest = UInt(regsWidth.W)
  val rfWen = Bool()

  val src = Vec(numSrc, UInt(xlen.W))
  val imm = UInt(xlen.W)
  val pc = UInt(addrBits.W)
  val isRVC = Bool()

  val pred_taken = Bool()
  val brtype = Bool()

  // debug
  val instr = UInt(32.W)
}

class IDUInterface(val parameter: IDUParameter) extends IDUBundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val in = Flipped(Decoupled(new IBUF2IDU(parameter.addrBits)))
  val out = Decoupled(new DecodeIO(parameter))
}

@instantiable
class IDU(val parameter: IDUParameter)
    extends FixedIORawModule(new IDUInterface(parameter))
    with SerializableModule[IDUParameter]
    with BtbDecode
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val decodeResult = Decoder.decode(parameter.decoderParam)(io.in.bits.inst)
  val instr = io.in.bits.inst

  io.out.bits.srcIsReg(0) := decodeResult(Decoder.ReadRs1)
  io.out.bits.srcIsReg(1) := decodeResult(Decoder.ReadRs2)
  io.out.bits.fuType := decodeResult(Decoder.Fu)
  io.out.bits.fuOpType := decodeResult(Decoder.FuOp)
  io.out.bits.lsrc(0) := io.in.bits.inst(19, 15)
  io.out.bits.lsrc(1) := io.in.bits.inst(24, 20)
  io.out.bits.ldest := io.in.bits.inst(11, 7)
  io.out.bits.rfWen := decodeResult(Decoder.WriteRd)

  val imm = MuxLookup(decodeResult(Decoder.ImmType), 0.U)(
    Seq(
      InstrType.I -> SignExt(io.in.bits.inst(31, 20), parameter.xlen),
      InstrType.S -> SignExt(Cat(io.in.bits.inst(31, 25), io.in.bits.inst(11, 7)), parameter.xlen),
      InstrType.B -> SignExt(
        Cat(io.in.bits.inst(31), io.in.bits.inst(7), io.in.bits.inst(30, 25), io.in.bits.inst(11, 8), 0.U(1.W)),
        parameter.xlen
      ),
      InstrType.U -> SignExt(Cat(io.in.bits.inst(31, 12), 0.U(12.W)), parameter.xlen),
      InstrType.J -> SignExt(
        Cat(io.in.bits.inst(31), io.in.bits.inst(19, 12), io.in.bits.inst(20), io.in.bits.inst(30, 21), 0.U(1.W)),
        parameter.xlen
      )
    )
  )
  // src 可能会在isu中被修改
  io.out.bits.src(0) := Mux(io.in.bits.inst(6, 0) === "b0110111".U, 0.U, io.in.bits.pc) // fix lui
  io.out.bits.src(1) := imm
  io.out.bits.imm := imm

  io.out.bits.pred_taken := io.in.bits.pred_taken
  io.out.bits.pc := io.in.bits.pc
  io.out.bits.isRVC := io.in.bits.isRVC

  io.out.bits.brtype := Mux(
    isRet(instr),
    Brtype.ret,
    Mux(isCall(instr), Brtype.call, Mux(FuType.isbrh(decodeResult(Decoder.Fu)), Brtype.branch, Brtype.jump))
  )

  io.in.ready := io.out.ready
  io.out.valid := io.in.valid

  // debug
  io.out.bits.instr := instr
}

//TODO: 移到IFU作为pre-decode?
trait BtbDecode {
  def isRVC(inst: UInt): Bool = (inst(1, 0) =/= 3.U)

  // def C_JAL     = BitPat("b????????????????_?01_?_??_???_??_???_01") // RV32C
  def C_J = BitPat("b????????????????_101_?_??_???_??_???_01")
  def C_JALR = BitPat("b????????????????_100_?_??_???_00_000_10") // c.jalr & c.jr
  def JAL = BitPat("b????????????????_???_?????_1101111")
  def JALR = BitPat("b????????????????_000_?????_1100111")

  def isjal(inst:  UInt): Bool = { inst === C_J || inst === JAL } // || (inst === C_JAL)
  def isjalr(inst: UInt): Bool = { inst === C_JALR || inst === JALR }

  def isLink(reg: UInt): Bool = reg === 1.U || reg === 5.U

  def rd(inst:     UInt): UInt = { Mux(isRVC(inst), inst(12), inst(11, 7)) }
  def rs(inst:     UInt): UInt = { Mux(isRVC(inst), Mux(isjal(inst), 0.U, inst(11, 7)), inst(19, 15)) }
  def isCall(inst: UInt): Bool = { (isjal(inst) && !isRVC(inst) || isjalr(inst)) && isLink(rd(inst)) } // Only for RV64
  def isRet(inst:  UInt): Bool = { isjalr(inst) && isLink(rs(inst)) && !isCall(inst) }
}
