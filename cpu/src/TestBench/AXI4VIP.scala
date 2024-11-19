package cpu.cpuemu.vip

import chisel3._
import chisel3.util.circt.dpi.{
  RawClockedNonVoidFunctionCall,
  RawUnclockedNonVoidFunctionCall,
  RawClockedVoidFunctionCall
}
import chisel3.util.{isPow2, log2Ceil}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import amba.axi4._

class WritePayload(length: Int, dataWidth: Int) extends Bundle {
  val data = Vec(length, UInt(dataWidth.W))
  // For dataWidth <= 8, align strb to u8 for a simple C-API
  val strb = Vec(length, UInt(math.max(8, dataWidth / 8).W))
}

// TODO: consider adding the latency of the read transaction
class ReadPayload(length: Int, dataWidth: Int) extends Bundle {
  val data = Vec(length, UInt(dataWidth.W))
}

class AXI4VIPInterface(parameter: AXI4BundleParameter) extends Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val axi = Flipped(AXI4(parameter))
}

@instantiable
class AXI4VIP(val parameter: AXI4BundleParameter) 
    extends FixedIORawModule(new AXI4VIPInterface(parameter))
    with SerializableModule[AXI4BundleParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset
  dontTouch(io)

  val writePayloadSize = 8
  val readPayloadSize = 8
  val outstanding = 8

  parameter.isRO match {
    case false => 
        new ReadManager
        new WriteManager
    case true  => 
        new ReadManager
    
  }

  private class WriteManager{
    withClockAndReset(io.clock, io.reset) {
      require(!parameter.isRO, "WriteManager should not be instantiated in Read-Only mode")

      /** There is an aw in the register. */
      val awIssued = RegInit(false.B)

      /** There is a w in the register. */
      val last = RegInit(false.B)

      /** memory to store the write payload
        * @todo
        *   limit the payload size based on the RTL configuration.
        */
      val writePayload =
        RegInit(0.U.asTypeOf(new WritePayload(writePayloadSize, parameter.dataWidth)))

      /** AWID, latch at AW fire, used at B fire. */
      val awid     = RegInit(0.U.asTypeOf(chiselTypeOf(io.axi.aw.bits.id)))
      val awaddr   = RegInit(0.U.asTypeOf(chiselTypeOf(io.axi.aw.bits.addr)))
      val awlen    = RegInit(0.U.asTypeOf(chiselTypeOf(io.axi.aw.bits.len)))
      val awsize   = RegInit(0.U.asTypeOf(chiselTypeOf(io.axi.aw.bits.size)))
      val awburst  = RegInit(0.U.asTypeOf(chiselTypeOf(io.axi.aw.bits.burst)))
      val awlock   = RegInit(0.U.asTypeOf(chiselTypeOf(io.axi.aw.bits.lock)))
      val awcache  = RegInit(0.U.asTypeOf(chiselTypeOf(io.axi.aw.bits.cache)))
      val awprot   = RegInit(0.U.asTypeOf(chiselTypeOf(io.axi.aw.bits.prot)))
      val awqos    = RegInit(0.U.asTypeOf(chiselTypeOf(io.axi.aw.bits.qos)))
      val awregion = RegInit(0.U.asTypeOf(chiselTypeOf(io.axi.aw.bits.region)))
      val awuser   = RegInit(0.U.asTypeOf(chiselTypeOf(io.axi.aw.bits.user)))

      /** index the payload, used to write [[writePayload]] */
      val writeIdx  = RegInit(0.U.asTypeOf(UInt(8.W)))
      val bFire     = io.axi.b.ready && io.axi.b.valid
      val awFire    = io.axi.aw.ready && io.axi.aw.valid
      val wLastFire = io.axi.w.valid && io.axi.w.ready && io.axi.w.bits.last
      val awExist   = io.axi.aw.valid || awIssued
      val wExist    = io.axi.w.valid && io.axi.w.bits.last || last

      // AW
      io.axi.aw.ready := !awIssued || (wExist && io.axi.b.ready)
      when(io.axi.aw.ready && io.axi.aw.valid) {
        awid     := io.axi.aw.bits.id
        awaddr   := io.axi.aw.bits.addr
        awlen    := io.axi.aw.bits.len
        awsize   := io.axi.aw.bits.size
        awburst  := io.axi.aw.bits.burst
        awlock   := io.axi.aw.bits.lock
        awcache  := io.axi.aw.bits.cache
        awprot   := io.axi.aw.bits.prot
        awqos    := io.axi.aw.bits.qos
        awregion := io.axi.aw.bits.region
        awuser   := io.axi.aw.bits.user
      }
      when(awFire ^ bFire) {
        awIssued := awFire
      }

      // W
      val writePayloadUpdate = WireDefault(writePayload)
      io.axi.w.ready := !last || (awExist && io.axi.b.ready)
      when(io.axi.w.valid && io.axi.w.ready) {
        writePayload.data(writeIdx)       := io.axi.w.bits.data
        writePayloadUpdate.data(writeIdx) := io.axi.w.bits.data
        writePayload.strb(writeIdx)       := io.axi.w.bits.strb.pad(writePayload.strb.getWidth)
        writePayloadUpdate.strb(writeIdx) := io.axi.w.bits.strb.pad(writePayload.strb.getWidth)
        writeIdx                          := writeIdx + 1.U
        when(io.axi.w.bits.last) {
          writeIdx := 0.U
        }
      }
      when(io.axi.w.valid && io.axi.w.ready && io.axi.w.bits.last ^ io.axi.b.ready) {
        last := io.axi.w.bits.last
      }

      // B
      io.axi.b.valid := awExist && wExist
      io.axi.b.id    := Mux(awIssued, awid, io.axi.aw.bits.id)
      io.axi.b.resp  := 0.U(2.W) // OK
      io.axi.b.user  := DontCare
      // TODO: add latency to the write transaction reply
      when(io.axi.b.valid && io.axi.b.ready) {
        RawClockedVoidFunctionCall(s"axi_write_${parameter.name}")(
          io.clock,
          when.cond && !io.gateWrite,
          io.channelId,
          // handle AW and W at same beat.
          Mux(awIssued, awid.asTypeOf(UInt(64.W)), io.axi.aw.bits.id),
          Mux(awIssued, awaddr.asTypeOf(UInt(64.W)), io.axi.aw.bits.addr),
          Mux(awIssued, awlen.asTypeOf(UInt(64.W)), io.axi.aw.bits.len),
          Mux(awIssued, awsize.asTypeOf(UInt(64.W)), io.axi.aw.bits.size),
          Mux(awIssued, awburst.asTypeOf(UInt(64.W)), io.axi.aw.bits.burst),
          Mux(awIssued, awlock.asTypeOf(UInt(64.W)), io.axi.aw.bits.lock),
          Mux(awIssued, awcache.asTypeOf(UInt(64.W)), io.axi.aw.bits.cache),
          Mux(awIssued, awprot.asTypeOf(UInt(64.W)), io.axi.aw.bits.prot),
          Mux(awIssued, awqos.asTypeOf(UInt(64.W)), io.axi.aw.bits.qos),
          Mux(awIssued, awregion.asTypeOf(UInt(64.W)), io.axi.aw.bits.region),
          writePayloadUpdate
        )
      }
    }
  }

  private class ReadManager {
    withClockAndReset(io.clock, io.reset) {
      class CAMValue extends Bundle {
        val arid             = UInt(16.W)
        val arlen            = UInt(8.W)
        val readPayload      = new ReadPayload(readPayloadSize, parameter.dataWidth)
        val readPayloadIndex = UInt(8.W)
        val valid            = Bool()
      }

      /** CAM to maintain order of read requests. This is maintained as FIFO. */
      val cam: Vec[CAMValue] = RegInit(0.U.asTypeOf(Vec(outstanding, new CAMValue)))
      require(isPow2(parameter.outstanding), "Need to handle pointers")
      val arPtr = RegInit(0.U.asTypeOf(UInt(log2Ceil(parameter.outstanding).W)))
      val rPtr  = RegInit(0.U.asTypeOf(UInt(log2Ceil(parameter.outstanding).W)))

      // AR
      io.axi.ar.ready := !cam(arPtr).valid
      when(io.axi.ar.ready && io.axi.ar.valid) {
        cam(arPtr).arid             := io.axi.ar.bits.id
        cam(arPtr).arlen            := io.axi.ar.bits.len
        cam(arPtr).readPayload      := RawUnclockedNonVoidFunctionCall(
          s"axi_read_${parameter.name}",
          new ReadPayload(parameter.readPayloadSize, parameter.dataWidth)
        )(
          when.cond && !io.gateRead,
          io.channelId,
          io.axi.ar.bits.id.asTypeOf(UInt(64.W)),
          io.axi.ar.bits.addr.asTypeOf(UInt(64.W)),
          io.axi.ar.bits.len.asTypeOf(UInt(64.W)),
          io.axi.ar.bits.size.asTypeOf(UInt(64.W)),
          io.axi.ar.bits.burst.asTypeOf(UInt(64.W)),
          io.axi.ar.bits.lock.asTypeOf(UInt(64.W)),
          io.axi.ar.bits.cache.asTypeOf(UInt(64.W)),
          io.axi.ar.bits.prot.asTypeOf(UInt(64.W)),
          io.axi.ar.bits.qos.asTypeOf(UInt(64.W)),
          io.axi.ar.bits.region.asTypeOf(UInt(64.W))
        )
        cam(arPtr).readPayloadIndex := 0.U
        cam(arPtr).valid            := true.B
        arPtr                       := arPtr + 1.U
      }

      // R
      io.axi.r.valid := cam(rPtr).valid
      io.axi.r.bits.id    := cam(rPtr).arid
      io.axi.r.bits.data  := cam(rPtr).readPayload.data(cam(rPtr).readPayloadIndex)
      io.axi.r.bits.resp  := 0.U // OK
      io.axi.r.bits.last  := (cam(rPtr).arlen === cam(rPtr).readPayloadIndex) && cam(rPtr).valid
      io.axi.r.bits.user  := DontCare
      when(io.axi.r.ready && io.axi.r.valid) {
        // increase index
        cam(rPtr).readPayloadIndex := cam(rPtr).readPayloadIndex + 1.U
        when(io.axi.r.bits.last) {
          cam(rPtr).valid := false.B
          rPtr            := rPtr + 1.U
        }
      }
    }
  }
}

