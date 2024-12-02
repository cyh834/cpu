package cpu.frontend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}

import utility._
import cpu._

class IFUInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val in = Flipped(Decoupled(new IFUReq(parameter.DataBits, parameter.VAddrBits)))
  val out = Decoupled(new IFU2IBUF(parameter.VAddrBits))
}

@instantiable
class IFU(val parameter: CPUParameter)
    extends FixedIORawModule(new IFUInterface(parameter))
    with SerializableModule[CPUParameter]
    with PreDecode
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  io.in.ready := io.out.ready
  // TODO: 一次取指保留多个指令
  val offset = io.in.bits.pc(2, 1) << 4
  val inst = io.in.bits.data >> offset
  val isrvc = false.B // isRVC(inst)

  io.out.bits.pc := io.in.bits.pc
  io.out.bits.inst := Mux(isrvc, inst(15, 0), inst(31, 0))
  io.out.bits.pred_taken := io.in.bits.pred_taken
  io.out.bits.isRVC := isrvc
  io.out.valid := io.in.valid
}

//TODO: 支持32位
trait PreDecode {
  def isRVC(inst: UInt): Bool = (inst(1, 0) =/= 3.U)

  // def C_JAL     = BitPat("b????????????????_?01_?_??_???_??_???_01") // RV32C
  // def C_J       = BitPat("b????????????????_101_?_??_???_??_???_01")
  // def C_JALR    = BitPat("b????????????????_100_?_??_???_00_000_10")  // c.jalr & c.jr
  // def JAL       = BitPat("b????????????????_???_?????_1101111")
  // def JALR      = BitPat("b????????????????_000_?????_1100111")

  // def isjal  (inst: UInt): Bool = {inst === C_J    || inst === JAL  } // || (inst === C_JAL)
  // def isjalr (inst: UInt): Bool = {inst === C_JALR || inst === JALR }

  // def isLink (reg :UInt) : Bool = reg === 1.U || reg === 5.U

  // def rd     (inst: UInt): UInt = {Mux(isRVC(inst), inst(12), inst(11,7))}
  // def rs     (inst: UInt): UInt = {Mux(isRVC(inst), Mux(isjal(inst), 0.U, inst(11, 7)), inst(19, 15))}
  // def isCall (inst: UInt): Bool = {(isjal(inst) && !isRVC(inst) || isjalr(inst)) && isLink(rd(inst))} // Only for RV64
  // def isRet  (inst: UInt): Bool = {isjalr(inst) && isLink(rs(inst)) && !isCall(inst)}
}
