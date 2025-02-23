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
}

@instantiable
class IFU(val parameter: CPUParameter)
    extends FixedIORawModule(new IFUInterface(parameter))
    with SerializableModule[CPUParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset
  
  //***********************/
  // IF1
  //***********************/
  // pc
  val pc = RegInit(parameter.ResetVector.U(parameter.VAddrBits.W))
  val pcInstValid = Wire(UInt(4.W))
  val pcUpdate = io.redirect.valid || io.imem.req.fire

  val snpc = Cat(pc(parameter.VAddrBits-1, 3), 0.U(3.W)) + 8.U //+ CacheReadWidth.U

  val bpu: Instance[BPU] = Instantiate(new BPU(parameter.bpuParameter))

  val crosslineJump = bpu.io.out.bits.crosslineJump
  val s_idle :: s_crosslineJump :: Nil = Enum(2)
  val state = RegInit(s_idle)
  switch(state){
    is(s_idle){
      when(pcUpdate && crosslineJump && !io.redirect.valid){ state := s_crosslineJump }
    }
    is(s_crosslineJump){
      when(pcUpdate || io.redirect.valid){ state := s_idle }
    }
  }
  val crosslineJumpTarget = RegEnable(bpu.io.out.bits.pc, crosslineJump && pcUpdate)
  
  // next pc
  val npc = MuxCase(
    snpc,
    Array(
      io.redirect.valid -> io.redirect.target,
      (state === s_crosslineJump) -> crosslineJumpTarget,
      (bpu.io.out.valid && !crosslineJump) -> bpu.io.out.bits.pc,
    )
  )

  bpu.io.clock := io.clock
  bpu.io.reset := io.reset
  bpu.io.flush := false.B
  bpu.io.in.bits.pc := npc
  bpu.io.in.valid := io.imem.req.fire
  bpu.io.update <> io.bpuUpdate

  pcInstValid :=  Mux((state === s_crosslineJump) && !io.redirect.valid, "b0001".U, (Fill(4, 1.U(1.W)) << pc(2, 1))(3, 0))

  io.imem.req.valid := io.out.ready || !io.reset.asBool || !io.redirect.valid
  io.imem.req.bits.pc := pc
  io.imem.req.bits.brIdx := pcInstValid & bpu.io.out.bits.brIdx.asUInt
  io.imem.req.bits.instValid := pcInstValid

  when (pcUpdate) { pc := npc }

  //***********************/
  // IF2
  //***********************/
  // 得到指令
  io.imem.resp.ready := io.out.ready
  io.out.bits.pc := io.imem.resp.bits.pc
  io.out.bits.inst := io.imem.resp.bits.inst
  io.out.bits.brIdx := io.imem.resp.bits.brIdx
  io.out.bits.instValid := io.imem.resp.bits.instValid
  io.out.valid := io.imem.resp.fire && !io.redirect.valid

}