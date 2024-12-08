package cpu.cache

import chisel3._
import chisel3.util._
import cpu._
import amba.axi4._
import amba.axi4.AXI4Parameters._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import cpu.backend.fu._

class InstUncacheInterface(useAsyncReset: Boolean, parameter: AXI4BundleParameter, vaddrBits: Int, dataBits: Int)
    extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (useAsyncReset) AsyncReset() else Bool())
  val flush = Input(Bool())
  val flush_rvc = Input(Bool())
  val req = Flipped(Decoupled(new PredictIO(vaddrBits)))
  val resp = Decoupled(new IFUReq(dataBits, vaddrBits))
  val mem = AXI4(parameter)
}

class InstUncache(useAsyncReset: Boolean, parameter: AXI4BundleParameter, vaddrBits: Int, dataBits: Int)
    extends FixedIORawModule(new InstUncacheInterface(useAsyncReset, parameter, vaddrBits, dataBits))
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val CntSize = 8
  val cnt = RegInit(0.U(log2Up(CntSize).W))
  when(io.mem.ar.fire && !io.mem.r.fire) {
    cnt := cnt + 1.U
  }
  when(io.mem.r.fire && !io.mem.ar.fire) {
    cnt := cnt - 1.U
  }
  assert(cnt < (CntSize - 1).U, "Uncache: cnt overflow")

  val needFlush = RegInit(false.B)
  when(io.flush || io.flush_rvc) {
    needFlush := true.B
  }
  when(cnt === 0.U) {
    needFlush := false.B
  }
  val id = 0.U

  io.mem.ar.valid := io.req.valid && !io.flush && !io.flush_rvc && !io.reset.asBool
  io.req.ready := io.mem.ar.ready

  io.mem.ar.bits.addr := Cat(io.req.bits.pc(parameter.addrWidth - 1, 2), 0.U(2.W))
  io.mem.ar.bits.id := id
  io.mem.ar.bits.len := 0.U
  io.mem.ar.bits.size := 2.U
  io.mem.ar.bits.burst := burst.INCR
  io.mem.ar.bits.lock := 0.U
  io.mem.ar.bits.cache := 0.U
  io.mem.ar.bits.prot := 0.U
  io.mem.ar.bits.qos := 0.U
  io.mem.ar.bits.region := 0.U
  io.mem.ar.bits.user := 0.U

  val flush = needFlush || io.flush
  io.resp.valid := io.mem.r.valid && !flush
  io.mem.r.ready := flush || io.resp.ready

  io.resp.bits.data := io.mem.r.bits.data
  io.resp.bits.pc := RegEnable(io.req.bits.pc, io.mem.ar.fire)
  io.resp.bits.pred_taken := RegEnable(io.req.bits.pred_taken, io.mem.ar.fire)
}

class DataUncacheInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val flush = Input(Bool())
  val load = Flipped(new LoadInterface(parameter))
  val store = Flipped(new StoreInterface(parameter))
  val mem = AXI4RWIrrevocable(parameter.loadStoreAXIParameter)
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

  when(io.mem.b.fire && !io.mem.aw.fire && !io.mem.w.fire) {
    storeCnt.pop()
  }
  when(io.mem.aw.fire && io.mem.w.fire) {
    storeCnt.push()
  }

  // ar
  // 写完才能读
  val loadValid = !io.reset.asBool && storeCnt.isEmpty
  io.mem.ar.valid := io.load.req.valid && loadValid
  io.load.req.ready := io.mem.ar.ready && loadValid

  io.mem.ar.bits.addr := io.load.req.bits.addr
  io.mem.ar.bits.id := 0.U
  io.mem.ar.bits.len := 0.U
  io.mem.ar.bits.size := io.load.req.bits.size
  io.mem.ar.bits.burst := burst.INCR
  io.mem.ar.bits.lock := 0.U
  io.mem.ar.bits.cache := 0.U
  io.mem.ar.bits.prot := 0.U
  io.mem.ar.bits.qos := 0.U
  io.mem.ar.bits.region := 0.U
  io.mem.ar.bits.user := 0.U

  // r
  io.mem.r.ready := io.load.resp.ready
  io.load.resp.valid := io.mem.r.valid && !io.reset.asBool
  io.load.resp.bits.data := io.mem.r.bits.data

  // aw
  // 写满了不能写
  val storeValid = !io.reset.asBool && !storeCnt.isFull
  io.mem.aw.valid := io.store.req.valid && storeValid
  io.store.req.ready := io.mem.aw.ready && storeValid

  io.mem.aw.bits.addr := io.store.req.bits.addr
  io.mem.aw.bits.id := 0.U
  io.mem.aw.bits.len := 0.U
  io.mem.aw.bits.size := io.store.req.bits.size
  io.mem.aw.bits.burst := burst.INCR
  io.mem.aw.bits.lock := 0.U
  io.mem.aw.bits.cache := 0.U
  io.mem.aw.bits.prot := 0.U
  io.mem.aw.bits.qos := 0.U
  io.mem.aw.bits.region := 0.U
  io.mem.aw.bits.user := 0.U

  // w
  io.mem.w.valid := io.store.req.valid && storeValid
  io.store.req.ready := io.mem.w.ready && storeValid

  io.mem.w.bits.data := io.store.req.bits.data
  io.mem.w.bits.strb := io.store.req.bits.strb
  io.mem.w.bits.last := true.B
  io.mem.w.bits.user := 0.U

  // b
  io.mem.b.ready := true.B
}
