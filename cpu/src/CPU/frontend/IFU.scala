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
  val reset  = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val imem = new IMEM
  val out = Decoupled(new IFU2IBUF(parameter.VAddrBits))
  val bpuUpdate = Input(Flipped(new BPUUpdate(parameter.bpuParameter)))
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
  val pc = RegInit(parameter.ResetVector.U(parameter.VAddrBits.W))
  val npc = Wire(UInt(parameter.VAddrBits.W))
  val bpu: Instance[BPU] = Instantiate(new BPU(parameter.bpuParameter))

  when(io.imem.req.fire) { pc := npc }

  // predict next pc
  bpu.io.in.bits.pc := pc
  bpu.io.in.valid := io.imem.req.fire
  bpu.io.flush := false.B

  // load inst
  io.imem.req.bits.addr := pc
  io.imem.req.valid := io.out.ready
  io.imem.resp.ready := io.out.ready

  // TODO: 一次取指保留多个指令
  val offset = pc(2, 1) << 16
  val inst = io.imem.resp.bits.data >> offset
  val isrvc = isRVC(inst)

  io.out.bits.pc := pc
  io.out.bits.inst := Mux(isrvc, inst(15, 0), inst(31, 0))
  io.out.bits.pred_taken := bpu.io.out.bits.pred_taken
  io.out.bits.isRVC := isrvc
  io.out.valid := io.imem.resp.valid

  // update pc
  npc := Mux(bpu.io.out.bits.pred_taken, bpu.io.out.bits.target, Mux(isrvc, pc + 2.U, pc + 4.U))

  // TODO: update ras in IFU?
  // update ras
  // bpu.io.ras_update.bits.brtype := Mux(isRet(inst), Brtype.ret,
  //                                 Mux(isCall(inst), Brtype.call, Brtype.X))
  // bpu.io.ras_update.bits.isRVC  := isrvc
  // bpu.io.ras_update.valid := (isRet(inst) || isCall(inst)) && io.out.fire
  bpu.io.update := io.bpuUpdate
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
