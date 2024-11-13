package cpu.cache

import chisel3._
import chisel3.util._
import cpu._
import amba.axi4._
import amba.axi4.AXI4Parameters._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}

class InsUncacheReq extends Bundle {
  val addr = UInt(64.W)
}

class InsUncacheResp extends Bundle {
  val data = UInt(64.W)
}

class InstrUncacheIO extends Bundle {
  val req = DecoupledIO(new InsUncacheReq)
  val resp = Flipped(DecoupledIO(new InsUncacheResp))
}

class InstrUncacheInterface(useAsyncReset: Boolean, parameter: AXI4BundleParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (useAsyncReset) AsyncReset() else Bool())
  val ifu = Flipped(new InstrUncacheIO)
  val mem = AXI4(parameter)
  val flush = Input(Bool())
}

class InstrUncache(useAsyncReset: Boolean, parameter: AXI4BundleParameter)
    extends FixedIORawModule(new InstrUncacheInterface(useAsyncReset, parameter))
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

  io.mem.ar.valid := io.ifu.req.valid && !io.flush
  io.ifu.req.ready := io.mem.ar.ready

  io.mem.ar.bits.addr := io.ifu.req.bits.addr
  io.mem.ar.bits.id := id
  io.mem.ar.bits.len := 0.U
  io.mem.ar.bits.size := 3.U
  io.mem.ar.bits.burst := burst.INCR
  io.mem.ar.bits.lock := 0.U
  io.mem.ar.bits.cache := 0.U
  io.mem.ar.bits.prot := 0.U
  io.mem.ar.bits.qos := 0.U
  io.mem.ar.bits.user := 0.U

  val flush = needFlush || io.flush
  io.ifu.resp.valid := io.mem.r.valid && !flush
  io.mem.r.ready := flush || io.ifu.resp.ready

  io.ifu.resp.bits.data := io.mem.r.bits.data

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
