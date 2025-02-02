package cpu.frontend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}

import utility._
import cpu._

class IFUReq(vaddrBits: Int) extends Bundle {
  val pc = UInt(vaddrBits.W)
  val brIdx = UInt(4.W)
  val instValid = UInt(4.W)
}

class IFUResp(vaddrBits: Int, dataBits: Int) extends IFUReq(vaddrBits) {
  val inst = UInt(dataBits.W)
}

class IMEMInterface(vaddrBits: Int, dataBits: Int) extends Bundle {
  val req = Decoupled(new IFUReq(vaddrBits))
  val resp = Flipped(Decoupled(new IFUResp(vaddrBits, dataBits)))
}

class IFUInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())

  val imem = new IMEMInterface(parameter.VAddrBits, parameter.XLEN)
  val out = Decoupled(new IFU2IBUF(parameter.VAddrBits))

  val bpuUpdate = Input(new BPUUpdate(parameter.bpuParameter))
  val redirect = Input(new RedirectIO(parameter.VAddrBits))
  val flush_uncache = Output(Bool())
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
  
  //***********************/
  // IF1
  //***********************/
  // pc
  val pc = RegInit(parameter.ResetVector.U(parameter.VAddrBits.W))
  val pcInstValid = RegInit("b1111".U)
  val pcUpdate = io.redirect.valid || io.imem.req.fire

  // sequence next pc
  val snpc = Cat(pc(parameter.VAddrBits-1, 3), 0.U(3.W)) + 8.U //+ CacheReadWidth.U

  val bpu: Instance[BPU] = Instantiate(new BPU(parameter.bpuParameter))
  
  val crosslineJump = bpu.io.out.bits.crosslineJump
  val s_idle :: s_crosslineJump :: Nil = Enum(2)
  val state = RegInit(s_idle)
  switch(state) {
    is(s_idle) {
      when(pcUpdate && crosslineJump && !io.redirect.valid) { state := s_crosslineJump }
    }
    is(s_crosslineJump) {
      when(pcUpdate || crosslineJump) { state := s_idle }
    }
  }
  val crosslineJumpTarget = RegEnable(bpu.io.out.bits.pc, crosslineJump && pcUpdate)

  // predicted next pc
  val pnpc = Mux(crosslineJump, snpc, bpu.io.out.bits.pc)

  // next pc
  val npc = MuxCase(
    snpc,
    Array(
      io.redirect.valid -> io.redirect.target,
      (state === s_crosslineJump) -> crosslineJumpTarget,
      bpu.io.out.bits.jump -> pnpc,
    )
  )

  bpu.io.clock := io.clock
  bpu.io.reset := io.reset
  bpu.io.flush := false.B
  bpu.io.in.bits.pc := npc
  bpu.io.in.valid := io.imem.req.fire
  bpu.io.update <> io.bpuUpdate

  val brIdx = Wire(UInt(4.W))
  // predicted branch position index, 4 bit vector
  val pbrIdx = bpu.io.out.bits.brIdx.asUInt | (crosslineJump << 3)
  brIdx := Mux(io.redirect.valid, 0.U, Mux(state === s_crosslineJump, 0.U, pbrIdx))

  io.imem.req.valid := io.out.ready || io.redirect.valid
  io.imem.req.bits.pc := pc
  io.imem.req.bits.brIdx := pcInstValid & brIdx
  io.imem.req.bits.instValid := pcInstValid

  def genInstValid(pc: UInt) = (Fill(4, 1.U(1.W)) << pc(2, 1))(3, 0)

  when (pcUpdate) {
    pc := npc
    pcInstValid := Mux(crosslineJump && !(state === s_crosslineJump) && !io.redirect.valid, "b0001".U, genInstValid(npc))
  }

  ///***********************/
  //// IF2
  ////***********************/
  //// 拓展 RVC 指令，返回 32 位指令
  //def expand(inst: UInt): UInt = {
  //  val exp = Instantiate(new RVCExpander(RVCExpanderParameter(parameter.XLEN, true)))
  //  exp.io.in := inst
  //  exp.io.out
  //}

  // 得到指令
  io.imem.resp.ready := io.out.ready
  io.out.bits.pc := io.imem.resp.bits.pc
  io.out.bits.inst := io.imem.resp.bits.inst
  io.out.bits.brIdx := io.imem.resp.bits.brIdx
  io.out.bits.instValid := io.imem.resp.bits.instValid
  io.out.valid := io.imem.resp.fire

  io.flush_uncache := false.B
  //// TODO: 一次取指保留多个指令
  //val offset = io.resp.bits.pc(2, 1) << 4
  //val inst = io.resp.bits.data >> offset
  //isrvc := isRVC(inst)

  //// 只有一半的指令
  //val half_inst_valid = !isrvc && (io.resp.bits.pc(2, 1) === 3.U)
  //val half_inst_valid_reg = RegEnable(half_inst_valid, io.resp.fire)
  //val half_inst = RegEnable(inst(15, 0), io.resp.fire)
  //val half_pc = RegEnable(io.resp.bits.pc, io.resp.fire)

  //io.out.bits.pc := Mux(half_inst_valid_reg, half_pc, io.resp.bits.pc)
  //io.out.bits.inst := Mux(half_inst_valid_reg, Cat(inst(15,0), half_inst), 
  //                    Mux(isrvc, expand(inst(15, 0)), 
  //                    inst(31, 0)))
  //io.out.bits.pred_taken := 0.U    //io.in.bits.pred_taken // no use
  //io.out.bits.isRVC := isrvc
  //io.out.valid := io.resp.fire && !half_inst_valid
}

//TODO: 支持32位
trait PreDecode {
  def isRVC(inst: UInt): Bool = (inst(1, 0) =/= 3.U)

  //def C_JAL     = BitPat("b????????????????_?01_?_??_???_??_???_01") // RV32C
  //def C_J       = BitPat("b????????????????_101_?_??_???_??_???_01")
  //def C_JALR    = BitPat("b????????????????_100_?_??_???_00_000_10")  // c.jalr & c.jr
  //def JAL       = BitPat("b????????????????_???_?????_1101111")
  //def JALR      = BitPat("b????????????????_000_?????_1100111")

  //def isjal  (inst: UInt): Bool = {inst === C_J    || inst === JAL  } // || (inst === C_JAL)
  //def isjalr (inst: UInt): Bool = {inst === C_JALR || inst === JALR }

  //def isLink (reg :UInt) : Bool = reg === 1.U || reg === 5.U

  //def rd     (inst: UInt): UInt = {Mux(isRVC(inst), inst(12), inst(11,7))}
  //def rs     (inst: UInt): UInt = {Mux(isRVC(inst), Mux(isjal(inst), 0.U, inst(11, 7)), inst(19, 15))}
  //def isCall (inst: UInt): Bool = {(isjal(inst) && !isRVC(inst) || isjalr(inst)) && isLink(rd(inst))} // Only for RV64
  //def isRet  (inst: UInt): Bool = {isjalr(inst) && isLink(rs(inst)) && !isCall(inst)}
}
