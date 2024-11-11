package cpu.frontend

import chisel3._
import chisel3.util.BitPat
import chisel3.util.experimental.decode._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.properties.{AnyClassType, Class, Property}
import chisel3.util.{DecoupledIO, Valid}
import org.chipsalliance.rvdecoderdb.Instruction

import cpu._
import utility._

object IDUParameter {
  implicit def rwP: upickle.default.ReadWriter[IDUParameter] =
    upickle.default.macroRW
}

case class IDUParameter(LogicRegsWidth: Int, XLEN: Int) extends SerializableModuleParameter {
  val numSrc = 2
}

class IDUProbe(parameter: IDUParameter) extends Bundle {
  val instr = UInt(32.W)
  val pc = UInt(parameter.VAddrBits.W)
  val isRVC = Bool()
}

class DecodeIO(parameter: IDUParameter) extends Bundle {
  val srcType = Vec(parameter.numSrc, SrcType())
  val fuType = FuType()
  val fuOpType = FuOpType()
  val lsrc = Vec(parameter.numSrc, UInt(parameter.LogicRegsWidth.W))
  val ldest = UInt(parameter.LogicRegsWidth.W)
  val rfWen = Bool()

  val src = Vec(parameter.numSrc, UInt(parameter.XLEN.W))
  val imm = UInt(parameter.XLEN.W)

  val pred_taken = Bool()
  val brtype = Bool()

  val probe = Output(Probe(new IDUProbe(parameter), layers.Verification))
}

class IDUInterface(parameter: IDUParameter) extends Bundle {
  val in = Flipped(Decoupled(new IBUF2IDU(parameter.VAddrBits)))
  val out = Decoupled(new DecodeIO(parameter))
}

@instantiable
class IDU(parameter: IDUParameter)
    extends FixedIORawModule(new IDUInterface(parameter))
    with SerializableModule[IDUParameter]
    with BtbDecode {

  val decodeResult = Decoder.decode(param)(io.in.bits.inst)

  io.out.bits.srcType(0) := decodeResult(ReadSrc1)
  io.out.bits.srcType(1) := decodeResult(ReadSrc2)
  io.out.bits.fuType := decodeResult(FuType)
  io.out.bits.fuOpType := decodeResult(FuOpType)
  io.out.bits.lsrc(0) := io.in.bits.inst(19, 15)
  io.out.bits.lsrc(1) := io.in.bits.inst(24, 20)
  io.out.bits.ldest := io.in.bits.inst(11, 7)
  io.out.bits.rfWen := decodeResult(WriteRd)

  // src 可能会在isu中被修改
  io.out.src(0) := io.in.bits.pc
  io.out.src(1) := decodeResult(Imm)
  io.out.imm := decodeResult(Imm)

  io.out.pred_taken := io.in.bits.pred_taken

  val instr = io.in.bits.inst
  io.out.brtype := Mux(
    isRet(instr),
    Brtype.ret,
    Mux(isCall(instr), Brtype.call, Mux(decode.isbru(decodeResult(FuType)), Brtype.branch, Brtype.jump))
  )

  io.in.ready := io.out.ready
  io.out.valid := io.in.valid

  val probeWire: IDUProbe = Wire(new IDUProbe(parameter))
  define(io.out.probe, ProbeValue(probeWire))
  probeWire.instr := instr
  probeWire.pc := io.in.bits.pc
  probeWire.isRVC := isRVC(instr)
}

//TODO: 移到IDU作为pre-decode?
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
