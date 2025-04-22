package cpu.device

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.properties.{AnyClassType, Class, Property}
import chisel3.util._

import amba.axi4._
import utility._

object CLINTParameter {
  implicit def rwP: upickle.default.ReadWriter[CLINTParameter] =
    upickle.default.macroRW
}

case class CLINTParameter(
    val base: Long = 0x38000000L,
    val size: Long = 0x10000,
    val AXIParameter: AXI4BundleParameter
) extends SerializableModuleParameter {
    val start = base
    val end = base + size
}

/** Interface of [[CLINT]]. */
class ClintIO extends Bundle {
  val mtip = Output(Bool())
  val msip = Output(Bool())
}

class CLINTInterface(parameter: CLINTParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val axi = Flipped(new AXI4RWIrrevocable(parameter.AXIParameter))
  val clint = new ClintIO
}

@instantiable
class CLINT(val parameter: CLINTParameter) 
    extends FixedIORawModule(new CLINTInterface(parameter)) 
    with SerializableModule[CLINTParameter] 
    with ImplicitClock 
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val in = io.axi

  val mtime = RegInit(0.U(64.W))  // unit: us
  val mtimecmp = RegInit(0.U(64.W))
  val msip = RegInit(0.U(64.W))

  val clk = 40
  val freq_reg = RegInit(clk.U(64.W))
  val freq = freq_reg(15, 0)
  val inc_reg = RegInit(1.U(64.W))
  val inc = inc_reg(15, 0)

  val cnt = RegInit(0.U(16.W))
  val nextCnt = cnt + 1.U
  cnt := Mux(nextCnt < freq, nextCnt, 0.U)
  val tick = (nextCnt === freq)
  when (tick) { mtime := mtime + inc }

  val mapping = Map(
    RegMap(0x0, msip),
    RegMap(0x4000, mtimecmp),
    RegMap(0x8000, freq_reg),
    RegMap(0x8008, inc_reg),
    RegMap(0xbff8, mtime)
  )
  def getOffset(addr: UInt) = addr(15,0)

  val raddr = Wire(UInt())
  val waddr = Wire(UInt())
  RegMap.generate(mapping, getOffset(raddr), in.r.bits.data,
    getOffset(waddr), in.w.fire, in.w.bits.data, MaskExpand(in.w.bits.strb))

  io.clint.mtip := RegNext(mtime >= mtimecmp)
  io.clint.msip := RegNext(msip =/= 0.U)

  // axi
  val s_idle :: s_rdata :: s_wdata :: s_wresp :: Nil = Enum(4)

  val state = RegInit(s_idle)

  switch(state){
    is(s_idle){
      when(in.ar.fire){
        state := s_rdata
      }
      when(in.aw.fire){
        state := s_wdata
      }
    }
    is(s_rdata){
      when(in.r.fire && in.r.bits.last){
        state := s_idle
      }
    }
    is(s_wdata){
      when(in.w.fire && in.w.bits.last){
        state := s_wresp
      }
    }
    is(s_wresp){
      when(in.b.fire){
        state := s_idle
      }
    }
  }

   val fullMask = MaskExpand(in.w.bits.strb)

  def genWdata(originData: UInt) = (originData & (~fullMask).asUInt) | (in.w.bits.data & fullMask)

  val (readBeatCnt, rLast, arlen) = {
    val c = Counter(256)
    val len = HoldUnless (in.ar.bits.len,in.ar.fire)
    raddr := HoldUnless (in.ar.bits.addr,in.ar.fire)
    in.r.bits.last := (c.value === len)

    when(in.r.fire) {
      c.inc()
      when(in.r.bits.last) {
        c.value := 0.U
      }
    }

    (c.value, in.r.bits.last, len)
  }

  in.ar.ready := state === s_idle
  in.r.bits.resp := AXI4Parameters.resp.OKAY
  in.r.valid := state === s_rdata


  val (writeBeatCnt, wLast) = {
    val c = Counter(256)
    waddr := HoldUnless (in.aw.bits.addr,in.aw.fire)
    when(in.w.fire) {
      c.inc()
      when(in.w.bits.last) {
        c.value := 0.U
      }
    }
    (c.value, in.w.bits.last)
  }

  val wdata = in.w.bits.data

  in.aw.ready := state === s_idle && !in.ar.valid
  in.w.ready := state === s_wdata

  in.b.bits.resp := AXI4Parameters.resp.OKAY
  in.b.valid := state===s_wresp

  in.b.bits.id := RegEnable(in.aw.bits.id, in.aw.fire)
  in.b.bits.user := RegEnable(in.aw.bits.user, in.aw.fire)
  in.r.bits.id := RegEnable(in.ar.bits.id, in.ar.fire)
  in.r.bits.user := RegEnable(in.ar.bits.user, in.ar.fire)
}

