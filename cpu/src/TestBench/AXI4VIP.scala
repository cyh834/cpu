package cpu.cpuemu.vip

import chisel3._
import chisel3.util.circt.dpi.{
  RawClockedNonVoidFunctionCall,
  RawClockedVoidFunctionCall,
  RawUnclockedNonVoidFunctionCall
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

case class AXI4VIPParameter(
  name:             String,
  axiParameter:     AXI4BundleParameter,
  outstanding:      Int,
  readPayloadSize:  Int,
  writePayloadSize: Int)

class AXI4VIPInterface(parameter: AXI4VIPParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Reset())
  val channelId: UInt = Input(Const(UInt(64.W)))
  val channel = Flipped(AXI4(parameter.axiParameter))
}

class AXI4VIP(parameter: AXI4VIPParameter) extends FixedIORawModule[AXI4VIPInterface](new AXI4VIPInterface(parameter)) {
  dontTouch(io)

  io.channel match {
    case channel: AXI4RWIrrevocable =>
      new WriteManager(channel)
      new ReadManager(channel)
    case channel: AXI4ROIrrevocable =>
      new ReadManager(channel)
  }

  private class WriteManager(channel: HasAW with HasW with HasB) {
    withClockAndReset(io.clock, io.reset) {

      /** There is an aw in the register. */
      val awIssued = RegInit(false.B)

      /** There is a w in the register. */
      val last = RegInit(false.B)

      /** memory to store the write payload
        * @todo
        *   limit the payload size based on the RTL configuration.
        */
      val writePayload =
        RegInit(0.U.asTypeOf(new WritePayload(parameter.writePayloadSize, parameter.axiParameter.dataWidth)))

      /** AWID, latch at AW fire, used at B fire. */
      val awid = RegInit(0.U.asTypeOf(chiselTypeOf(channel.aw.bits.id)))
      val awaddr = RegInit(0.U.asTypeOf(chiselTypeOf(channel.aw.bits.addr)))
      val awlen = RegInit(0.U.asTypeOf(chiselTypeOf(channel.aw.bits.len)))
      val awsize = RegInit(0.U.asTypeOf(chiselTypeOf(channel.aw.bits.size)))
      val awburst = RegInit(0.U.asTypeOf(chiselTypeOf(channel.aw.bits.burst)))
      val awlock = RegInit(0.U.asTypeOf(chiselTypeOf(channel.aw.bits.lock)))
      val awcache = RegInit(0.U.asTypeOf(chiselTypeOf(channel.aw.bits.cache)))
      val awprot = RegInit(0.U.asTypeOf(chiselTypeOf(channel.aw.bits.prot)))
      val awqos = RegInit(0.U.asTypeOf(chiselTypeOf(channel.aw.bits.qos)))
      val awregion = RegInit(0.U.asTypeOf(chiselTypeOf(channel.aw.bits.region)))
      val awuser = RegInit(0.U.asTypeOf(chiselTypeOf(channel.aw.bits.user)))

      /** index the payload, used to write [[writePayload]] */
      val writeIdx = RegInit(0.U.asTypeOf(UInt(8.W)))
      val bFire = channel.b.ready && channel.b.valid
      val awFire = channel.aw.ready && channel.aw.valid
      val wLastFire = channel.w.valid && channel.w.ready && channel.w.bits.last
      val awExist = channel.aw.valid || awIssued
      val wExist = channel.w.valid && channel.w.bits.last || last

      // AW
      channel.aw.ready := !awIssued || (wExist && channel.b.ready)
      when(channel.aw.ready && channel.aw.valid) {
        awid := channel.aw.bits.id
        awaddr := channel.aw.bits.addr
        awlen := channel.aw.bits.len
        awsize := channel.aw.bits.size
        awburst := channel.aw.bits.burst
        awlock := channel.aw.bits.lock
        awcache := channel.aw.bits.cache
        awprot := channel.aw.bits.prot
        awqos := channel.aw.bits.qos
        awregion := channel.aw.bits.region
        awuser := channel.aw.bits.user
      }
      when(awFire ^ bFire) {
        awIssued := awFire
      }

      // W
      val writePayloadUpdate = WireDefault(writePayload)
      channel.w.ready := !last || (awExist && channel.b.ready)
      when(channel.w.valid && channel.w.ready) {
        writePayload.data(writeIdx) := channel.w.bits.data
        writePayloadUpdate.data(writeIdx) := channel.w.bits.data
        writePayload.strb(writeIdx) := channel.w.bits.strb.pad(writePayload.strb.getWidth)
        writePayloadUpdate.strb(writeIdx) := channel.w.bits.strb.pad(writePayload.strb.getWidth)
        writeIdx := writeIdx + 1.U
        when(channel.w.bits.last) {
          writeIdx := 0.U
        }
      }
      when(channel.w.valid && channel.w.ready && channel.w.bits.last ^ channel.b.ready) {
        last := channel.w.bits.last
      }

      // B
      channel.b.valid := awExist && wExist
      channel.b.bits.id := Mux(awIssued, awid, channel.aw.bits.id)
      channel.b.bits.resp := 0.U(2.W) // OK
      channel.b.bits.user := DontCare
      // TODO: add latency to the write transaction reply
      when(channel.b.valid && channel.b.ready) {
        RawClockedVoidFunctionCall(s"axi_write")(
          io.clock,
          when.cond,
          io.channelId,
          // handle AW and W at same beat.
          Mux(awIssued, awid.asTypeOf(UInt(64.W)), channel.aw.bits.id),
          Mux(awIssued, awaddr.asTypeOf(UInt(64.W)), channel.aw.bits.addr),
          Mux(awIssued, awlen.asTypeOf(UInt(64.W)), channel.aw.bits.len),
          Mux(awIssued, awsize.asTypeOf(UInt(64.W)), channel.aw.bits.size),
          Mux(awIssued, awburst.asTypeOf(UInt(64.W)), channel.aw.bits.burst),
          Mux(awIssued, awlock.asTypeOf(UInt(64.W)), channel.aw.bits.lock),
          Mux(awIssued, awcache.asTypeOf(UInt(64.W)), channel.aw.bits.cache),
          Mux(awIssued, awprot.asTypeOf(UInt(64.W)), channel.aw.bits.prot),
          Mux(awIssued, awqos.asTypeOf(UInt(64.W)), channel.aw.bits.qos),
          Mux(awIssued, awregion.asTypeOf(UInt(64.W)), channel.aw.bits.region),
          writePayloadUpdate
        )
      }
    }
  }

  // read manager
  private class ReadManager(channel: HasAR with HasR) {
    withClockAndReset(io.clock, io.reset) {
      class CAMValue extends Bundle {
        val arid = UInt(16.W)
        val arlen = UInt(8.W)
        val readPayload = new ReadPayload(parameter.readPayloadSize, parameter.axiParameter.dataWidth)
        val readPayloadIndex = UInt(8.W)
        val valid = Bool()
      }

      /** CAM to maintain order of read requests. This is maintained as FIFO. */
      val cam: Vec[CAMValue] = RegInit(0.U.asTypeOf(Vec(parameter.outstanding, new CAMValue)))
      require(isPow2(parameter.outstanding), "Need to handle pointers")
      val arPtr = RegInit(0.U.asTypeOf(UInt(log2Ceil(parameter.outstanding).W)))
      val rPtr = RegInit(0.U.asTypeOf(UInt(log2Ceil(parameter.outstanding).W)))

      // AR
      channel.ar.ready := !cam(arPtr).valid
      when(channel.ar.ready && channel.ar.valid) {
        cam(arPtr).arid := channel.ar.bits.id
        cam(arPtr).arlen := channel.ar.bits.len
        cam(arPtr).readPayload := RawUnclockedNonVoidFunctionCall( //Unclocked or Clocked?
          s"axi_read",
          new ReadPayload(parameter.readPayloadSize, parameter.axiParameter.dataWidth)
        )(
          //io.clock,
          when.cond,
          io.channelId,
          channel.ar.bits.id.asTypeOf(UInt(64.W)),
          channel.ar.bits.addr.asTypeOf(UInt(64.W)),
          channel.ar.bits.len.asTypeOf(UInt(64.W)),
          channel.ar.bits.size.asTypeOf(UInt(64.W)),
          channel.ar.bits.burst.asTypeOf(UInt(64.W)),
          channel.ar.bits.lock.asTypeOf(UInt(64.W)),
          channel.ar.bits.cache.asTypeOf(UInt(64.W)),
          channel.ar.bits.prot.asTypeOf(UInt(64.W)),
          channel.ar.bits.qos.asTypeOf(UInt(64.W)),
          channel.ar.bits.region.asTypeOf(UInt(64.W))
        )
        cam(arPtr).readPayloadIndex := 0.U
        cam(arPtr).valid := true.B
        arPtr := arPtr + 1.U
      }

      // R
      channel.r.valid := cam(rPtr).valid
      channel.r.bits.id := cam(rPtr).arid
      channel.r.bits.data := cam(rPtr).readPayload.data(cam(rPtr).readPayloadIndex)
      channel.r.bits.resp := 0.U // OK
      channel.r.bits.last := (cam(rPtr).arlen === cam(rPtr).readPayloadIndex) && cam(rPtr).valid
      channel.r.bits.user := DontCare
      when(channel.r.ready && channel.r.valid) {
        // increase index
        cam(rPtr).readPayloadIndex := cam(rPtr).readPayloadIndex + 1.U
        when(channel.r.bits.last) {
          cam(rPtr).valid := false.B
          rPtr := rPtr + 1.U
        }
      }
    }
  }
}
