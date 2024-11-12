package cpu.frontend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.properties.{AnyClassType, Class, Property}
import org.chipsalliance.rvdecoderdb.Instruction

import cpu._
import utility._

object IDUParameter {
  implicit def rwP: upickle.default.ReadWriter[IDUParameter] =
    upickle.default.macroRW
}

case class IDUParameter(addrBits: Int, numSrc: Int, regsWidth: Int, xlen: Int ) extends SerializableModuleParameter

trait IDUBundle extends Bundle {
  val parameter: IDUParameter
  val addrBits = parameter.addrBits
  val regsWidth = parameter.regsWidth
  val numSrc = parameter.numSrc
  val xlen = parameter.xlen
}

class IDUProbe(parameter: IDUParameter) extends IDUBundle {
  val instr = UInt(32.W)
}

class DecodeIO(parameter: IDUParameter) extends IDUBundle {
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

  val probe = Output(Probe(new IDUProbe(parameter), layers.Verification))
}

class IDUInterface(parameter: IDUParameter) extends IDUBundle {
  val in = Flipped(Decoupled(new IBUF2IDU(addrBits)))
  val out = Decoupled(new DecodeIO(parameter))
}

@instantiable
class IDU(parameter: IDUParameter)
    extends FixedIORawModule(new IDUInterface(parameter))
    with SerializableModule[IDUParameter]
    with BtbDecode {

  val decodeResult = Decoder.decode(param)(io.in.bits.inst)

  io.out.bits.srcIsReg(0) := decodeResult(ReadRs1)
  io.out.bits.srcIsReg(1) := decodeResult(ReadRs2)
  io.out.bits.fuType := decodeResult(Fu)
  io.out.bits.fuOpType := decodeResult(FuOp)
  io.out.bits.lsrc(0) := io.in.bits.inst(19, 15)
  io.out.bits.lsrc(1) := io.in.bits.inst(24, 20)
  io.out.bits.ldest := io.in.bits.inst(11, 7)
  io.out.bits.rfWen := decodeResult(WriteRd)

  // src 可能会在isu中被修改
  io.out.src(0) := io.in.bits.pc
  io.out.src(1) := decodeResult(Imm)
  io.out.imm := decodeResult(Imm)

  io.out.pred_taken := io.in.bits.pred_taken
  io.out.pc := io.in.bits.pc
  io.out.isRVC := isRVC(instr)

  val instr = io.in.bits.inst
  io.out.brtype := Mux(
    isRet(instr),
    Brtype.ret,
    Mux(isCall(instr), Brtype.call, Mux(decode.isbru(decodeResult(Fu)), Brtype.branch, Brtype.jump))
  )

  io.in.ready := io.out.ready
  io.out.valid := io.in.valid

  val probeWire: IDUProbe = Wire(new IDUProbe(parameter))
  define(io.out.probe, ProbeValue(probeWire))
  probeWire.instr := instr
}

//TODO: 移到IFU作为pre-decode?
trait BtbDecode extends HascpuParameter {
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
