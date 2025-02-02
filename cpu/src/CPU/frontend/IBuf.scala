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
  val bufferDepth: Int = 8  // 新增缓冲深度参数
  val maxReadNum: Int = 1   // 每周期最大输出指令数
  val maxWriteNum: Int = 4  // 每周期最大写入指令数
}

class IBUF2IDU(vaddrBits: Int) extends Bundle {
  val pc = UInt(vaddrBits.W)
  val inst = UInt(32.W)
  val isRVC = Bool()
  val pred_taken = Bool()
}

class IBUFInterface(parameter: IBUFParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val flush = Input(Bool())
  val in = Flipped(Decoupled(new IFU2IBUF(parameter.vaddrBits)))
  val out = Decoupled(new IBUF2IDU(parameter.vaddrBits))
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
  (0 to 3).map(i => isRVC(i.U) := instVec(i.U)(1,0) =/= 3.U)

  // 有效指令选择逻辑
  val enqSlots = Wire(Vec(4, Bool()))
  enqSlots(0) := instValid(0)
  enqSlots(1) := instValid(1) && !(brIdx(0) && !isRVC(0))
  enqSlots(2) := instValid(2) && !brIdx(0) && !(brIdx(1) && !isRVC(1))
  enqSlots(3) := instValid(3) && !brIdx(0) && !brIdx(1) && !(brIdx(2) && !isRVC(2))

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
  def write_fifo(idx: Int): Unit = {
    memReg(wr_ptr + idx.U).inst := instVec(idx.U)
    memReg(wr_ptr + idx.U).pc := io.in.bits.pc + (idx << 1).U
    memReg(wr_ptr + idx.U).isRVC := isRVC(idx.U)
    memReg(wr_ptr + idx.U).brIdx := brIdx(idx.U)
  }
  when(io.in.fire) {
    for(i <- 0 until 4) {
      when(enqSlots(i)) { write_fifo(i) }
    }
    wr_ptr := wr_ptr + PopCount(enqSlots)
  }

  io.in.ready := wrrs >= 4.U


  // 输出逻辑 TODO: 多发射
  // 拓展 RVC 指令，返回 32 位指令
  def expand(inst: UInt): UInt = {
    val exp = Instantiate(new RVCExpander(RVCExpanderParameter(parameter.xlen, true)))
    exp.io.in := inst
    exp.io.out
  }

  io.out.valid := rdrs =/= 0.U
  io.out.bits.pc := memReg(rd_ptr).pc
  io.out.bits.inst := Mux(memReg(rd_ptr).isRVC, expand(memReg(rd_ptr).inst), Cat(memReg(rd_ptr).inst, memReg(rd_ptr + 1.U).inst))
  io.out.bits.isRVC := memReg(rd_ptr).isRVC
  io.out.bits.pred_taken := memReg(rd_ptr).brIdx
  when(io.out.fire) {
    rd_ptr := Mux(memReg(rd_ptr).isRVC, rd_ptr + 1.U, rd_ptr + 2.U)
  }

  fifo_counter := fifo_counter + Mux(io.in.fire, PopCount(enqSlots), 0.U) - Mux(io.out.fire, Mux(memReg(rd_ptr).isRVC, 1.U, 2.U), 0.U)

  when(io.flush) {
    wr_ptr := 0.U
    rd_ptr := 0.U
    fifo_counter := 0.U
  }
}
