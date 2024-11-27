package cpu.cache

import chisel3._
import chisel3.util._
import cpu._
import amba.axi4._
import amba.axi4.AXI4Parameters._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}

class InstUncacheInterface(useAsyncReset: Boolean, parameter: AXI4BundleParameter, vaddrBits: Int, dataBits: Int) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (useAsyncReset) AsyncReset() else Bool())
  val flush = Input(Bool())
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
  val needFlush = RegInit(false.B)
  when(io.flush) {
    needFlush := true.B
  }
  when(io.mem.r.fire && !io.flush) {
    needFlush := false.B
  }
  val id = 0.U

  io.mem.ar.valid := io.req.valid && !io.flush && !io.reset.asBool
  io.req.ready := io.mem.ar.ready

  io.mem.ar.bits.addr := io.req.bits.pc(parameter.addrWidth-1, 0)
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
  if (parameter.isRW) {
    // io.mem.aw.valid := false.B
    // io.mem.aw.bits.addr := 0.U
    // io.mem.aw.bits.id := 0.U
    // io.mem.aw.bits.len := 0.U
    // io.mem.aw.bits.size := 0.U
    // io.mem.aw.bits.burst := 0.U
    // io.mem.aw.bits.lock := 0.U
    // io.mem.aw.bits.cache := 0.U
    // io.mem.aw.bits.prot := 0.U
    // io.mem.aw.bits.qos := 0.U
    // io.mem.aw.bits.user := 0.U
    // io.mem.w.valid := false.B
    // io.mem.w.bits.data := 0.U
    // io.mem.w.bits.strb := 0.U
    // io.mem.w.bits.last := false.B
    // io.mem.w.bits.user := 0.U
    // io.mem.b.ready := false.B
  }
}
