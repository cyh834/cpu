package cpu.frontend

import chisel3._
import chisel3.util._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}

import cpu._
import fifo._

object IBUFParameter {
  implicit def rwP: upickle.default.ReadWriter[IBUFParameter] =
    upickle.default.macroRW
}

case class IBUFParameter(useAsyncReset: Boolean, vaddrBits: Int, xlen: Int) extends SerializableModuleParameter {
  val bufferDepth: Int = 8 // 新增缓冲深度参数
  val maxReadNum:  Int = 1 // 每周期最大输出指令数
  val maxWriteNum: Int = 4 // 每周期最大写入指令数
}

class IBUF2IDU(vaddrBits: Int) extends Bundle {
  val pc = UInt(vaddrBits.W)
  val inst = UInt(32.W)
  val isRVC = Bool()
  val pred_taken = Bool()

  // debug
  val debug_inst = UInt(32.W) // 可能是16位的压缩指令
}

class IBUFInterface(parameter: IBUFParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val flush = Input(Bool())
  val in = Flipped(Decoupled(new IFU2IBUF(parameter.vaddrBits)))
  val out = Decoupled(new IBUF2IDU(parameter.vaddrBits))
  val redirect = new RedirectIO(parameter.vaddrBits)
}

// 指令缓存目前一次只能写入一条指令，遇到压缩指令就flush bpu, 未来再优化
@instantiable
class IBUF(val parameter: IBUFParameter)
    extends FixedIORawModule(new IBUFInterface(parameter))
    with SerializableModule[IBUFParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  // 输入处理流水线
  val instVec = io.in.bits.inst.asTypeOf(Vec(4, UInt(16.W)))
  val instValid = io.in.bits.instValid.asBools
  val brIdx = io.in.bits.brIdx
  val isRVC = Wire(Vec(4, Bool()))
  (0 to 3).map(i => isRVC(i.U) := instVec(i.U)(1, 0) =/= 3.U)

  // 有效指令选择逻辑
  val enqSlots = Wire(Vec(4, Bool()))
  enqSlots(0) := instValid(0)
  enqSlots(1) := instValid(1) && !(brIdx(0) && isRVC(0))
  enqSlots(2) := instValid(2) && !brIdx(0) && !(brIdx(1) && isRVC(1))
  enqSlots(3) := instValid(3) && !brIdx(0) && !brIdx(1) && !(brIdx(2) && isRVC(2))

  // 计算 shift
  val shift = Mux(enqSlots(0), 0.U, Mux(enqSlots(1), 1.U, Mux(enqSlots(2), 2.U, 3.U)))
  val enqsize = PopCount(enqSlots);
  val enqfire = (0 until 4).map(i => enqsize >= (i + 1).U)

  // fifo entry
  class RingEntry extends Bundle {
    val inst = UInt(16.W)
    val pc = UInt(parameter.vaddrBits.W)
    val brIdx = Bool()
    val isRVC = Bool()
  }

  val memReg = Reg(Vec(parameter.bufferDepth, new RingEntry)) // the register based memory

  val wr_ptr = RegInit(0.U(log2Up(parameter.bufferDepth).W)) // fifo write pointer
  val rd_ptr = RegInit(0.U(log2Up(parameter.bufferDepth).W)) // fifo read pointer
  val fifo_counter = RegInit(0.U(log2Up(parameter.bufferDepth + 1).W)) // fifo counter

  // Calculate remaining space
  val rdrs = fifo_counter
  val wrrs = parameter.bufferDepth.U - fifo_counter

  // 写入环形缓冲
  def write_fifo(idx: Int, shift: UInt): Unit = {
    memReg(wr_ptr + idx.U).inst := instVec(idx.U + shift)
    memReg(wr_ptr + idx.U).pc := Cat(io.in.bits.pc(parameter.vaddrBits - 1, 3), shift + idx.U, 0.U(1.W))
    memReg(wr_ptr + idx.U).isRVC := isRVC(idx.U + shift)
    memReg(wr_ptr + idx.U).brIdx := brIdx(idx.U + shift)
  }
  when(io.in.fire) {
    for (i <- 0 until 4) {
      when(enqfire(i)) { write_fifo(i, shift) }
    }
    wr_ptr := wr_ptr + enqsize
  }

  io.in.ready := wrrs >= 4.U

  // 输出逻辑 TODO: 多发射
  // 拓展 RVC 指令，返回 32 位指令
  def expand(inst: UInt): UInt = {
    val exp = Instantiate(new RVCExpander(RVCExpanderParameter(parameter.xlen, true)))
    exp.io.in := inst
    exp.io.out
  }

  io.out.valid := Mux(memReg(rd_ptr).isRVC, rdrs >= 1.U, rdrs >= 2.U)  && !io.redirect.valid
  io.out.bits.pc := memReg(rd_ptr).pc
  io.out.bits.inst := Mux(
    memReg(rd_ptr).isRVC,
    expand(memReg(rd_ptr).inst),
    Cat(memReg(rd_ptr + 1.U).inst, memReg(rd_ptr).inst)
  )
  io.out.bits.isRVC := memReg(rd_ptr).isRVC
  io.out.bits.pred_taken := memReg(rd_ptr).brIdx
  when(io.out.fire) {
    rd_ptr := Mux(memReg(rd_ptr).isRVC, rd_ptr + 1.U, rd_ptr + 2.U)
  }

  fifo_counter := fifo_counter + Mux(io.in.fire, PopCount(enqSlots), 0.U) - Mux(
    io.out.fire,
    Mux(memReg(rd_ptr).isRVC, 1.U, 2.U),
    0.U
  )

  // debug
  io.out.bits.debug_inst := Mux(memReg(rd_ptr).isRVC, Cat(0.U(16.W), memReg(rd_ptr).inst), Cat(memReg(rd_ptr + 1.U).inst, memReg(rd_ptr).inst))

  // predict crossline jump error
  io.redirect.valid := (rdrs >= 2.U) && !memReg(rd_ptr).isRVC && ((memReg(rd_ptr).pc + 2.U) =/= memReg(rd_ptr + 1.U).pc)
  io.redirect.target := (memReg(rd_ptr).pc + 2.U)
  when(io.redirect.valid) {
    rd_ptr := rd_ptr
    wr_ptr := rd_ptr + 1.U
    fifo_counter := 1.U
  }
  // update bpu?

  when(io.flush) {
    rd_ptr := 0.U
    wr_ptr := 0.U
    fifo_counter := 0.U
  }
  
}
