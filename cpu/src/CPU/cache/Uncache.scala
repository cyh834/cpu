package cpu.cache

import chisel3._
import chisel3.util._
import cpu._
import amba.axi4._
import amba.axi4.AXI4Parameters._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import cpu.backend.fu._
import cpu.frontend._

class InstUncacheInterface(useAsyncReset: Boolean, parameter: AXI4BundleParameter, vaddrBits: Int, dataBits: Int)
    extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (useAsyncReset) AsyncReset() else Bool())
  val flush = Input(Bool())

  // ifu
  val in = Flipped(new IMEMInterface(vaddrBits, dataBits))
  // mem
  val out = AXI4(parameter)
}

class InstUncache(useAsyncReset: Boolean, parameter: AXI4BundleParameter, vaddrBits: Int, dataBits: Int)
    extends FixedIORawModule(new InstUncacheInterface(useAsyncReset, parameter, vaddrBits, dataBits))
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  // val CntSize = 8
  // val cnt = RegInit(0.U(log2Up(CntSize).W))
  // when(io.out.ar.fire && !io.out.r.fire) {
  //  cnt := cnt + 1.U
  //  assert(cnt + 1.U <= CntSize.U, "InstUncache: cnt overflow")
  // }
  // when(io.out.r.fire && !io.out.ar.fire) {
  //  cnt := cnt - 1.U
  //  assert(cnt === 0.U, "InstUncache: cnt underflow")
  // }
  val s_idle :: s_busy :: Nil = Enum(2)
  val state = RegInit(s_idle)
  when(io.out.ar.fire) {
    state := s_busy
  }
  when(io.out.r.fire) {
    state := s_idle
  }

  val needFlush = RegInit(false.B)
  when(io.flush && (((state === s_idle) && io.out.ar.fire) || (state === s_busy))) {
    needFlush := true.B
  }.elsewhen(state === s_idle) {
    needFlush := false.B
  }
  val id = 0.U

  io.out.ar.valid := io.in.req.valid && (state === s_idle) && !io.reset.asBool
  io.in.req.ready := io.out.ar.ready && (state === s_idle) && !io.reset.asBool

  // 默认值
  io.out.ar.bits := 0.U.asTypeOf(new AR(parameter))

  io.out.ar.bits.addr := Cat(io.in.req.bits.pc(parameter.addrWidth - 1, 3), 0.U(3.W))
  io.out.ar.bits.id := id
  io.out.ar.bits.len := 0.U
  io.out.ar.bits.size := 3.U
  io.out.ar.bits.burst := burst.INCR
  // io.out.ar.bits.lock := 0.U
  // io.out.ar.bits.cache := 0.U
  // io.out.ar.bits.prot := 0.U
  // io.out.ar.bits.qos := 0.U
  // io.out.ar.bits.region := 0.U
  // io.out.ar.bits.user := 0.U

  val flush = needFlush || io.flush
  io.in.resp.valid := io.out.r.valid && !flush
  io.out.r.ready := flush || io.in.resp.ready

  io.in.resp.bits.inst := io.out.r.bits.data
  io.in.resp.bits.pc := RegEnable(io.in.req.bits.pc, io.in.req.fire)
  io.in.resp.bits.brIdx := RegEnable(io.in.req.bits.brIdx, io.in.req.fire)
  io.in.resp.bits.instValid := RegEnable(io.in.req.bits.instValid, io.in.req.fire)
  // io.in.resp.bits.pred_taken := RegEnable(io.in.req.bits.pred_taken, io.in.ar.fire)
}

class DataUncacheInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val flush = Input(Bool())
  val load = Flipped(new LoadInterface(parameter))
  val store = Flipped(new StoreInterface(parameter))
  val out = AXI4RWIrrevocable(parameter.loadStoreAXIParameter)
}

class DataUncache(parameter: CPUParameter)
    extends FixedIORawModule(new DataUncacheInterface(parameter))
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  // 可以连续写
  // 写完了才能读
  val CntSize = 8 // 最多连续写次数

  class Cnt(size: Int) {
    val cnt = RegInit(0.U(log2Up(size).W))

    def isEmpty: Bool = cnt === 0.U
    def isFull:  Bool = cnt === (size - 1).U
    def push(): Unit = {
      cnt := cnt + 1.U
      assert(!isFull)
    }
    def pop(): Unit = {
      cnt := cnt - 1.U
      assert(!isEmpty)
    }
  }

  // 写计数器
  val storeCnt = new Cnt(CntSize)

  val store_fire = io.out.aw.fire && io.out.w.fire
  when(io.out.b.fire && !store_fire) {
    storeCnt.pop()
  }
  when(!io.out.b.fire && store_fire) {
    storeCnt.push()
  }

  // ar
  // 写完才能读
  val loadValid = !io.reset.asBool && storeCnt.isEmpty
  io.out.ar.valid := io.load.req.valid && loadValid
  io.load.req.ready := io.out.ar.ready && loadValid

  io.out.ar.bits.addr := io.load.req.bits.addr
  io.out.ar.bits.id := 0.U
  io.out.ar.bits.len := 0.U
  io.out.ar.bits.size := io.load.req.bits.size
  io.out.ar.bits.burst := burst.INCR
  io.out.ar.bits.lock := 0.U
  io.out.ar.bits.cache := 0.U
  io.out.ar.bits.prot := 0.U
  io.out.ar.bits.qos := 0.U
  io.out.ar.bits.region := 0.U
  io.out.ar.bits.user := 0.U

  // r
  io.out.r.ready := io.load.resp.ready
  io.load.resp.valid := io.out.r.valid && !io.reset.asBool
  io.load.resp.bits.data := io.out.r.bits.data

  // aw
  // 写满了不能写
  val storeValid = !io.reset.asBool && !storeCnt.isFull
  io.out.aw.valid := io.store.req.valid && storeValid
  io.store.req.ready := io.out.aw.ready && storeValid

  io.out.aw.bits.addr := io.store.req.bits.addr
  io.out.aw.bits.id := 0.U
  io.out.aw.bits.len := 0.U
  io.out.aw.bits.size := io.store.req.bits.size
  io.out.aw.bits.burst := burst.INCR
  io.out.aw.bits.lock := 0.U
  io.out.aw.bits.cache := 0.U
  io.out.aw.bits.prot := 0.U
  io.out.aw.bits.qos := 0.U
  io.out.aw.bits.region := 0.U
  io.out.aw.bits.user := 0.U

  // w
  io.out.w.valid := io.store.req.valid && storeValid
  io.store.req.ready := io.out.w.ready && storeValid

  io.out.w.bits.data := io.store.req.bits.data
  io.out.w.bits.strb := io.store.req.bits.strb
  io.out.w.bits.last := true.B
  io.out.w.bits.user := 0.U

  // b
  io.out.b.ready := true.B
}
