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
  val IBUFDepth = 8
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

  // 拓展 RVC 指令，返回 32 位指令
  def expand(inst: UInt): UInt = {
    val exp = Instantiate(new RVCExpander(RVCExpanderParameter(parameter.xlen, true)))
    exp.io.in := inst
    exp.io.out
  }

  // 修改输入数据
  val t0 = Wire(new IFU2IBUF(parameter.vaddrBits))
  t0.data := expand(io.in.bits.data)
  t0.pc := io.in.bits.pc
  t0.isRVC := io.in.bits.isRVC

  // ibuf
  val buf: Instance[SyncFIFO[IFU2IBUF]] = Instantiate(
    new SyncFIFO(new IFU2IBUF(parameter.vaddrBits), parameter.IBUFDepth, parameter.useAsyncReset)
  )

  val passthrough = io.in.fire && buf.io.empty
  io.in.ready := !buf.io.full
  io.out.valid := passthrough || !buf.io.empty && !io.flush
  io.out.bits := Mux(passthrough, t0, buf.io.data_out)

  buf.io.rd_en := io.out.fire && !passthrough
  buf.io.wr_en := io.in.fire && !passthrough
  buf.io.data_in := io.in.bits
  buf.io.flush := io.flush
  buf.io.clock := io.clock
  buf.io.reset := io.reset
}
