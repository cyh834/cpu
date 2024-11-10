package cpu.frontend.decoder

import chisel3._
import chisel3.util.BitPat
import chisel3.util.experimental.decode._
import org.chipsalliance.rvdecoderdb.Instruction

import cpu._
import utility._

object DecoderParam {
  implicit def rwP: upickle.default.ReadWriter[DecoderParam] = upickle.default.macroRW
}

case class DecoderParam(allInstructions: Seq[Instruction])

trait CPUDecodeFiled[D <: Data] extends DecodeField[CPUDecodePattern, D] with FieldName

trait BoolField extends CPUDecodeFiled[Bool] with BoolDecodeField[CPUDecodePattern]

trait SrcTypeUopField extends CPUDecodeFiled[UInt] with FieldName {
  def chiselType: UInt = UInt(1.W)
}

trait FuTypeUopField extends CPUDecodeFiled[UInt] with FieldName {
  def chiselType: UInt = UInt(4.W)
}

trait FuOpTypeUopField extends CPUDecodeFiled[UInt] with FieldName {
  def chiselType: UInt = UInt(7.W)
}

case class IDUParameter(LogicRegsWidth: Int, XLEN: Int) extends SerializableModuleParameter{
  val numSrc = 2
}

class CtrlSignalIO(parameter: IDUParameter) extends Bundle{
  val srcType         = Vec(parameter.numSrc, SrcType())
  val fuType          = FuType()
  val fuOpType        = FuOpType()
  val lsrc            = Vec(parameter.numSrc, UInt(parameter.LogicRegsWidth.W))
  val ldest           = UInt(parameter.LogicRegsWidth.W)
  val rfWen           = Bool()
  val iscpuTrap      = Bool()
  //val isSrc1Forward   = Bool()
  //val isSrc2Forward   = Bool()
  //val waitForward     = Bool() // no speculate execution
  //val isBlocked       = Bool() // This inst requires pipeline to be blocked
}

class DataSrcIO(parameter: IDUParameter) extends Bundle{
  val src  = Vec(parameter.numSrc, UInt(parameter.XLEN.W))
  val imm = UInt(parameter.XLEN.W)
}

class CtrlFlowIO(parameter: IDUParameter) extends Bundle{
  val instr = UInt(32.W)
  val pc = UInt(parameter.VAddrBits.W)
  //val pnpc = UInt(VAddrBits.W)
  //val redirect = new RedirectIO
  //val exceptionVec = ExceptionVec()
  //val intrVec = Vec(12, Bool())
  //val brIdx = UInt(4.W)
  val isRVC = Bool()
  val pred_taken = Bool()
  val brtype = Bool ()
  //val crossPageIPFFix = Bool()
  //val runahead_checkpoint_id = UInt(64.W)
}

class DecodeIO(parameter: IDUParameter) extends Bundle{
  val ctrl = new CtrlSignalIO
  val data = new DataSrcIO
  val flow = new CtrlFlowIO
}

class IDUInterface(parameter: IDUParameter) extends Bundle{
  val in = Flipped(Decoupled(new IBUF2IDU(parameter.VAddrBits)))
  val out = Decoupled(new DecodeIO(parameter))
}

class IDU (parameter: IDUParameter)
  extends FixedIORawModule(new IDUInterface(parameter))
  with SerializableModule[IDUParameter]
  with BtbDecode{

  val decoder = ListLookup(io.in.bits.inst, INVALID_INSTR.Error, RISCV.table)

  val srctype0 :: srctype1 :: fu :: fuop :: instrtype :: xWen :: cpuTrap :: noSpec :: blockBack :: flushPipe :: Nil = decoder
  
  val (ctrl, data, flow) = (io.out.bits.ctrl, io.out.bits.data, io.out.bits.flow)

  val isReg: Bool = SrcType.isReg(ctrl.fuType)
  ctrl.srcType(0) := srctype0
  ctrl.srcType(1) := srctype1
  ctrl.fuType     := fu
  ctrl.fuOpType   := fuop
  ctrl.lsrc(0)    := Mux(isReg, io.in.bits.inst(19, 15), 0.U)
  ctrl.lsrc(1)    := Mux(isReg, io.in.bits.inst(24, 20), 0.U)
  ctrl.ldest      := io.in.bits.inst(11, 7)
  ctrl.rfWen      := xWen
  ctrl.iscpuTrap := cpuTrap

  val instr = io.in.bits.inst
  val imm = MuxLookup(instrtype, 0.U)(Seq(
    InstrType.I -> SignExt(instr(31, 20) , XLEN),
    InstrType.S -> SignExt(Cat(instr(31, 25), instr(11, 7)), XLEN),
    InstrType.B -> SignExt(Cat(instr(31), instr(7), instr(30, 25), instr(11, 8), 0.U(1.W)) , XLEN),
    InstrType.U -> SignExt(Cat(instr(31, 12), 0.U(12.W)) , XLEN),
    InstrType.J -> SignExt(Cat(instr(31), instr(19, 12), instr(20), instr(30, 21), 0.U(1.W)) , XLEN)
  ))
  data.src(0) := Mux(isReg, 0.U, io.in.bits.pc)
  data.src(1) := Mux(isReg, 0.U, imm)
  data.imm    := imm

  flow.instr := instr
  flow.pc := io.in.bits.pc
  flow.isRVC := false.B
  flow.pred_taken := io.in.bits.pred_taken

  flow.brtype := Mux(isRet(instr), Brtype.ret, 
                 Mux(isCall(instr), Brtype.call,
                 Mux(FuType.isbrh(fu), Brtype.branch, Brtype.jump)))

  io.in.ready := io.out.ready
  io.out.valid := io.in.valid
}

//TODO: 优化冗余逻辑
trait BtbDecode extends HascpuParameter{
  def isRVC  (inst: UInt): Bool = (inst(1,0) =/= 3.U)

  // def C_JAL     = BitPat("b????????????????_?01_?_??_???_??_???_01") // RV32C
  def C_J       = BitPat("b????????????????_101_?_??_???_??_???_01")
  def C_JALR    = BitPat("b????????????????_100_?_??_???_00_000_10")  // c.jalr & c.jr
  def JAL       = BitPat("b????????????????_???_?????_1101111")
  def JALR      = BitPat("b????????????????_000_?????_1100111")

  def isjal  (inst: UInt): Bool = {inst === C_J    || inst === JAL  } // || (inst === C_JAL)
  def isjalr (inst: UInt): Bool = {inst === C_JALR || inst === JALR }

  def isLink (reg :UInt) : Bool = reg === 1.U || reg === 5.U

  def rd     (inst: UInt): UInt = {Mux(isRVC(inst), inst(12), inst(11,7))}
  def rs     (inst: UInt): UInt = {Mux(isRVC(inst), Mux(isjal(inst), 0.U, inst(11, 7)), inst(19, 15))}
  def isCall (inst: UInt): Bool = {(isjal(inst) && !isRVC(inst) || isjalr(inst)) && isLink(rd(inst))} // Only for RV64
  def isRet  (inst: UInt): Bool = {isjalr(inst) && isLink(rs(inst)) && !isCall(inst)}
}