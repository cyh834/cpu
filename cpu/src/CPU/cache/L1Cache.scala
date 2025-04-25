package cpu.cache

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.properties.{AnyClassType, Class, Property}
import chisel3.util._
import chisel3.util.experimental.decode.DecodePattern
import org.chipsalliance.rvdecoderdb.Instruction

import amba.axi4._
import cpu.frontend._
import cpu.backend._
import cpu.cache._
import cpu.frontend.decoder._
import utility._

object CacheParameter {
  implicit def rwP: upickle.default.ReadWriter[CacheParameter] =
    upickle.default.macroRW
}

case class CacheParameter(
  ro: Boolean = false,
  XLEN: Int = 64,
  PAddrBits: Int = 64,
  DataBits: Int = 64,
  AXIParameter: AXI4BundleParameter
) extends SerializableModuleParameter{
  val TotalSize: Int = 32 // Kbytes
  val Ways: Int = 4
  val LineSize = XLEN // byte
  val LineBeats = LineSize / 8 //DATA WIDTH 64
  val Sets = TotalSize * 1024 / LineSize / Ways
  val OffsetBits = log2Up(LineSize)
  val IndexBits = log2Up(Sets)
  val WordIndexBits = log2Up(LineBeats)
  val TagBits = PAddrBits - OffsetBits - IndexBits

  def addrBundle = new Bundle {
    val tag = UInt(TagBits.W)
    val index = UInt(IndexBits.W)
    val wordIndex = UInt(WordIndexBits.W)
    val byteOffset = UInt((if (XLEN == 64) 3 else 2).W)
  }

  def CacheMetaArrayReadBus() = new SRAMReadBus(new MetaBundle, set = Sets, way = Ways)
  def CacheDataArrayReadBus() = new SRAMReadBus(new DataBundle, set = Sets * LineBeats, way = Ways)
  def CacheMetaArrayWriteBus() = new SRAMWriteBus(new MetaBundle, set = Sets, way = Ways)
  def CacheDataArrayWriteBus() = new SRAMWriteBus(new DataBundle, set = Sets * LineBeats, way = Ways)

  def getMetaIdx(addr: UInt) = addr.asTypeOf(addrBundle).index
  def getDataIdx(addr: UInt) = Cat(addr.asTypeOf(addrBundle).index, addr.asTypeOf(addrBundle).wordIndex)

  def isSameWord(a1: UInt, a2: UInt) = ((a1 >> 2) === (a2 >> 2))
  def isSetConflict(a1: UInt, a2: UInt) = (a1.asTypeOf(addrBundle).index === a2.asTypeOf(addrBundle).index)
}

sealed class MetaBundle(implicit val cacheConfig: CacheParameter) extends Bundle {
  val tag = Output(UInt(cacheConfig.TagBits.W))
  val valid = Output(Bool())
  val dirty = Output(Bool())

  def apply(tag: UInt, valid: Bool, dirty: Bool) = {
    this.tag := tag
    this.valid := valid
    this.dirty := dirty
    this
  }
}

sealed class DataBundle(implicit val cacheConfig: CacheParameter) extends CacheBundle {
  val data = Output(UInt(DataBits.W))

  def apply(data: UInt) = {
    this.data := data
    this
  }
}


class CacheInterface(parameter: CacheParameter) extends Bundle{
  val clock = Input(Clock())
  val reset = Input(Bool())
  val flush = Input(UInt(2.W))
  val flushCache = Input(Bool())

  val in = Flipped(new DmemInterface(parameter.PAddrBits, parameter.DataBits))
  val out = new AXI4RWIrrevocable(parameter.AXIParameter)
  val mmio = new AXI4RWIrrevocable(parameter.AXIParameter)
  val empty = Output(Bool())
}

sealed class Stage1IO(implicit val cacheConfig: CacheParameter) extends CacheBundle {
  val req = Decoupled(new DmemReq(cacheConfig.PAddrBits, cacheConfig.DataBits))
}

sealed class CacheStage1(implicit val parameter: CacheParameter) extends Module {
  class CacheStage1IO extends Bundle {
    val in = Flipped(Decoupled(new DmemReq(cacheConfig.PAddrBits, cacheConfig.DataBits)))
    val out = Decoupled(new Stage1IO)
    val metaReadBus = CacheMetaArrayReadBus()
    val dataReadBus = CacheDataArrayReadBus()
  }
  val io = IO(new CacheStage1IO)

  // read meta array and data array
  val readBusValid = io.in.valid && io.out.ready
  io.metaReadBus.apply(valid = readBusValid, setIdx = getMetaIdx(io.in.bits.addr))
  io.dataReadBus.apply(valid = readBusValid, setIdx = getDataIdx(io.in.bits.addr))

  io.out.bits.req := io.in.bits
  io.out.valid := io.in.valid && io.metaReadBus.req.ready && io.dataReadBus.req.ready
  io.in.ready := (!io.in.valid || io.out.fire) && io.metaReadBus.req.ready && io.dataReadBus.req.ready
}

sealed class Stage2IO(implicit val parameter: CacheParameter) extends Bundle {
  val req = new DmemReq(parameter.PAddrBits, parameter.DataBits)
  val metas = Vec(parameter.Ways, new MetaBundle)
  val datas = Vec(parameter.Ways, new DataBundle)
  val hit = Output(Bool())
  val waymask = Output(UInt(parameter.Ways.W))
  val mmio = Output(Bool())
  val isForwardData = Output(Bool())
  val forwardData = Output(CacheDataArrayWriteBus().req.bits)
}

// check
sealed class CacheStage2(implicit val parameter: CacheParameter) extends Module {
  class CacheStage2IO extends Bundle {
    val in = Flipped(Decoupled(new Stage1IO))
    val out = Decoupled(new Stage2IO)
    val metaReadResp = Flipped(Vec(parameter.Ways, new MetaBundle))
    val dataReadResp = Flipped(Vec(parameter.Ways, new DataBundle))
    val metaWriteBus = Input(CacheMetaArrayWriteBus())
    val dataWriteBus = Input(CacheDataArrayWriteBus())
  }
  val io = IO(new CacheStage2IO)

  val req = io.in.bits.req
  val addr = req.addr.asTypeOf(addrBundle)

  val isForwardMeta = io.in.valid && io.metaWriteBus.req.valid && io.metaWriteBus.req.bits.setIdx === getMetaIdx(req.addr)
  val isForwardMetaReg = RegInit(false.B)
  when (isForwardMeta) { isForwardMetaReg := true.B }
  when (io.in.fire || !io.in.valid) { isForwardMetaReg := false.B }
  val forwardMetaReg = RegEnable(io.metaWriteBus.req.bits, isForwardMeta)

  val metaWay = Wire(Vec(Ways, chiselTypeOf(forwardMetaReg.data)))
  val pickForwardMeta = isForwardMetaReg || isForwardMeta
  val forwardMeta = Mux(isForwardMeta, io.metaWriteBus.req.bits, forwardMetaReg)
  val forwardWaymask = forwardMeta.waymask.getOrElse("1".U).asBools
  forwardWaymask.zipWithIndex.map { case (w, i) =>
    metaWay(i) := Mux(pickForwardMeta && w, forwardMeta.data, io.metaReadResp(i))
  }

  val hitVec = VecInit(metaWay.map(m => m.valid && (m.tag === addr.tag) && io.in.valid)).asUInt
  val victimWaymask = if (parameter.Ways > 1) (1.U << LFSR64()(log2Up(parameter.Ways)-1,0)) else "b1".U

  val invalidVec = VecInit(metaWay.map(m => !m.valid)).asUInt
  val hasInvalidWay = invalidVec.orR
  val refillInvalidWaymask = Mux(invalidVec >= 8.U, "b1000".U,
    Mux(invalidVec >= 4.U, "b0100".U,
    Mux(invalidVec >= 2.U, "b0010".U, "b0001".U)))

  val waymask = Mux(io.out.bits.hit, hitVec, Mux(hasInvalidWay, refillInvalidWaymask, victimWaymask))

  io.out.bits.metas := metaWay
  io.out.bits.hit := io.in.valid && hitVec.orR
  io.out.bits.waymask := waymask
  io.out.bits.datas := io.dataReadResp
  io.out.bits.mmio := AddressSpace.isMMIO(req.addr)

  val isForwardData = io.in.valid && (io.dataWriteBus.req match { case r =>
    r.valid && r.bits.setIdx === getDataIdx(req.addr)
  })
  val isForwardDataReg = RegInit(false.B)
  when (isForwardData) { isForwardDataReg := true.B }
  when (io.in.fire || !io.in.valid) { isForwardDataReg := false.B }
  val forwardDataReg = RegEnable(io.dataWriteBus.req.bits, isForwardData)
  io.out.bits.isForwardData := isForwardDataReg || isForwardData
  io.out.bits.forwardData := Mux(isForwardData, io.dataWriteBus.req.bits, forwardDataReg)

  io.out.bits.req <> req
  io.out.valid := io.in.valid
  io.in.ready := !io.in.valid || io.out.fire

}

// writeback
sealed class CacheStage3(implicit val parameter: CacheParameter) extends Module {
  class CacheStage3IO extends Bundle {
    val in = Flipped(Decoupled(new Stage2IO))
    val out = Decoupled(new AXI4RWIrrevocable(parameter.AXIParameter))
    val isFinish = Output(Bool())
    val flush = Input(Bool())
    val dataReadBus = CacheDataArrayReadBus()
    val dataWriteBus = CacheDataArrayWriteBus()
    val metaWriteBus = CacheMetaArrayWriteBus()

    val mem = new AXI4RWIrrevocable(parameter.AXIParameter)
    val mmio = new AXI4RWIrrevocable(parameter.AXIParameter)

  }
  val io = IO(new CacheStage3IO)

  val metaWriteArb = Module(new Arbiter(CacheMetaArrayWriteBus().req.bits, 2))
  val dataWriteArb = Module(new Arbiter(CacheDataArrayWriteBus().req.bits, 2))

  val req = io.in.bits.req
  val addr = req.addr.asTypeOf(addrBundle)
  val mmio = io.in.valid && io.in.bits.mmio
  val hit = io.in.valid && io.in.bits.hit
  val miss = io.in.valid && !io.in.bits.hit
  val probe = io.in.valid && hasCoh.B && req.isProbe()
  val hitReadBurst = hit && req.isReadBurst()
  val meta = Mux1H(io.in.bits.waymask, io.in.bits.metas)


  val useForwardData = io.in.bits.isForwardData && io.in.bits.waymask === io.in.bits.forwardData.waymask.getOrElse("b1".U)
  val dataReadArray = Mux1H(io.in.bits.waymask, io.in.bits.datas).data
  val dataRead = Mux(useForwardData, io.in.bits.forwardData.data.data, dataReadArray)
  val wordMask = Mux(!ro.B && req.isWrite(), MaskExpand(req.wmask), 0.U(DataBits.W))

  val hitWrite = hit && req.isWrite()
  val dataHitWriteBus = Wire(CacheDataArrayWriteBus()).apply(
    data = Wire(new DataBundle).apply(MaskData(dataRead, req.wdata, wordMask)),
    valid = hitWrite, setIdx = Cat(addr.index, Mux(req.cmd === SimpleBusCmd.writeBurst || req.isWriteLast(), writeL2BeatCnt.value, addr.wordIndex)), waymask = io.in.bits.waymask)

  val metaHitWriteBus = Wire(CacheMetaArrayWriteBus()).apply(
    valid = hitWrite && !meta.dirty, setIdx = getMetaIdx(req.addr), waymask = io.in.bits.waymask,
    data = Wire(new MetaBundle).apply(tag = meta.tag, valid = true.B, dirty = (!ro).B)
  )

  val s_idle :: s_memReadReq :: s_memReadResp :: s_memWriteReq :: s_memWriteResp :: s_mmioReq :: s_mmioResp :: s_wait_resp :: s_release :: Nil = Enum(9)
  val state = RegInit(s_idle)
  val needFlush = RegInit(false.B)

  when (io.flush && (state =/= s_idle)) { needFlush := true.B }
  when (io.out.fire && needFlush) { needFlush := false.B }

  val readBeatCnt = Counter(LineBeats)
  val writeBeatCnt = Counter(LineBeats)

  val s2_idle :: s2_dataReadWait :: s2_dataOK :: Nil = Enum(3)
  val state2 = RegInit(s2_idle)

  io.dataReadBus.apply(valid = (state === s_memWriteReq || state === s_release) && (state2 === s2_idle),
    setIdx = Cat(addr.index, Mux(state === s_release, readBeatCnt.value, writeBeatCnt.value)))
  val dataWay = RegEnable(io.dataReadBus.resp.data, state2 === s2_dataReadWait)
  val dataHitWay = Mux1H(io.in.bits.waymask, dataWay).data

  switch (state2) {
    is (s2_idle) { when (io.dataReadBus.req.fire) { state2 := s2_dataReadWait } }
    is (s2_dataReadWait) { state2 := s2_dataOK }
    is (s2_dataOK) { when (io.mem.req.fire || io.cohResp.fire || hitReadBurst && io.out.ready) { state2 := s2_idle } }
  }

  // critical word first read
  val raddr = Cat(req.addr(parameter.PAddrBits-1,3), 0.U(3.W))
  // dirty block addr
  val waddr = Cat(meta.tag, addr.index, 0.U(parameter.OffsetBits.W))
  val cmd = Mux(state === s_memReadReq, SimpleBusCmd.readBurst,
    Mux((writeBeatCnt.value === (parameter.LineBeats - 1).U), SimpleBusCmd.writeLast, SimpleBusCmd.writeBurst))
  io.mem.req.bits.apply(addr = Mux(state === s_memReadReq, raddr, waddr),
    cmd = cmd, size = (if (parameter.XLEN == 64) "b11".U else "b10".U),
    wdata = dataHitWay, wmask = Fill(DataBytes, 1.U))

  io.mem.resp.ready := true.B
  io.mem.req.valid := (state === s_memReadReq) || ((state === s_memWriteReq) && (state2 === s2_dataOK))

  // mmio
  io.mmio.req.bits := req
  io.mmio.resp.ready := true.B
  io.mmio.req.valid := (state === s_mmioReq)

  val afterFirstRead = RegInit(false.B)
  val alreadyOutFire = RegEnable(true.B, false.B, io.out.fire)
  val readingFirst = !afterFirstRead && io.mem.resp.fire && (state === s_memReadResp)
  val inRdataRegDemand = RegEnable(Mux(mmio, io.mmio.resp.bits.rdata, io.mem.resp.bits.rdata),
                                   Mux(mmio, state === s_mmioResp, readingFirst))

  val respToL1Fire = hitReadBurst && io.out.ready && state2 === s2_dataOK
  val respToL1Last = Counter((state === s_idle || state === s_release && state2 === s2_dataOK) && hitReadBurst && io.out.ready, LineBeats)._2

  switch (state) {
    is (s_idle) {
      afterFirstRead := false.B
      alreadyOutFire := false.B

      when ((miss || mmio) && !io.flush) {
        state := Mux(mmio, s_mmioReq, Mux(!parameter.ro.B && meta.dirty, s_memWriteReq, s_memReadReq))
      }
    }

    is (s_mmioReq) { when (io.mmio.req.fire) { state := s_mmioResp } }
    is (s_mmioResp) { when (io.mmio.resp.fire) { state := s_wait_resp } }

    is (s_memReadReq) { when (io.mem.req.fire) {
      state := s_memReadResp
      readBeatCnt.value := addr.wordIndex
    }}

    is (s_memReadResp) {
      when (io.mem.resp.fire) {
        afterFirstRead := true.B
        readBeatCnt.inc()
        when (io.mem.r.bits.last) { state := s_wait_resp }
      }
    }

    is (s_memWriteReq) {
      when (io.mem.w.fire) { writeBeatCnt.inc() }
      when (io.mem.w.bits.last && io.mem.w.fire) { state := s_memWriteResp }
    }

    is (s_memWriteResp) { when (io.mem.b.fire) { state := s_memReadReq } }
    is (s_wait_resp) { when (io.out.fire || needFlush || alreadyOutFire) { state := s_idle } }
  }

  val dataRefill = MaskData(io.mem.r.bits.data, req.wdata, Mux(readingFirst, wordMask, 0.U(DataBits.W)))
  val dataRefillWriteBus = Wire(CacheDataArrayWriteBus()).apply(
    valid = (state === s_memReadResp) && io.mem.r.fire, setIdx = Cat(addr.index, readBeatCnt.value),
    data = Wire(new DataBundle).apply(dataRefill), waymask = io.in.bits.waymask)

  dataWriteArb.io.in(0) <> dataHitWriteBus.req
  dataWriteArb.io.in(1) <> dataRefillWriteBus.req
  io.dataWriteBus.req <> dataWriteArb.io.out

  val metaRefillWriteBus = Wire(CacheMetaArrayWriteBus()).apply(
    valid = (state === s_memReadResp) && io.mem.r.fire && io.mem.r.bits.last,
    data = Wire(new MetaBundle).apply(valid = true.B, tag = addr.tag, dirty = !parameter.ro.B && req.isWrite()),
    setIdx = getMetaIdx(req.addr), waymask = io.in.bits.waymask
  )

  metaWriteArb.io.in(0) <> metaHitWriteBus.req
  metaWriteArb.io.in(1) <> metaRefillWriteBus.req
  io.metaWriteBus.req <> metaWriteArb.io.out

  io.out.valid := Mux(hit, true.B, Mux(req.isWrite() || mmio, state === s_wait_resp, afterFirstRead && !alreadyOutFire))

  io.in.ready := io.out.ready && (state === s_idle && !hitReadBurst) && !miss

}

class Cache(val parameter: CacheParameter)
    extends FixedIORawModule(new CacheInterface(parameter))
    with SerializableModule[CacheParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  // cpu pipeline
  val s1 = Module(new CacheStage1(parameter))
  val s2 = Module(new CacheStage2(parameter))
  val s3 = Module(new CacheStage3(parameter))
  val metaArray = Module(new SRAMTemplateWithArbiter(nRead = 1, new MetaBundle, set = parameter.Sets, way = parameter.Ways, shouldReset = true))
  val dataArray = Module(new SRAMTemplateWithArbiter(nRead = 2, new DataBundle, set = parameter.Sets * parameter.LineBeats, way = parameter.Ways))

  metaArray.reset := reset.asBool || io.flushCache

  s1.io.in <> io.in

  PipelineConnect(s1.io.out, s2.io.in, s2.io.out.fire, io.flush(0))
  PipelineConnect(s2.io.out, s3.io.in, s3.io.isFinish, io.flush(1))
  io.in.resp <> s3.io.out
  s3.io.flush := io.flush(1)
  io.out.mem <> s3.io.mem
  io.mmio <> s3.io.mmio
  io.empty := !s2.io.in.valid && !s3.io.in.valid

  io.in.resp.valid := s3.io.out.valid

  metaArray.io.r(0) <> s1.io.metaReadBus
  dataArray.io.r(0) <> s1.io.dataReadBus
  dataArray.io.r(1) <> s3.io.dataReadBus

  metaArray.io.w <> s3.io.metaWriteBus
  dataArray.io.w <> s3.io.dataWriteBus

  s2.io.metaReadResp := s1.io.metaReadBus.resp.data
  s2.io.dataReadResp := s1.io.dataReadBus.resp.data
  s2.io.dataWriteBus := s3.io.dataWriteBus
  s2.io.metaWriteBus := s3.io.metaWriteBus

}

object Cache {
  def apply(clock: Clock, reset: Bool, flush: UInt, flushCache: Bool, in: DmemInterface, mmio: AXI4RWIrrevocable, empty: Bool)(implicit parameter: CacheParameter) = {
    val cache = Instantiate(new Cache(parameter))

    cache.io.clock := clock
    cache.io.reset := reset
    cache.io.flush := flush
    cache.io.flushCache := flushCache
    cache.io.in <> in
    mmio <> cache.io.mmio
    empty := cache.io.empty
    cache.io.out
  }
}