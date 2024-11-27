package cpu.frontend

import chisel3._
import chisel3.util._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}

import utility._
import amba.axi4._
import cpu._
import cpu.cache.InstUncache

class FrontendInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val imem = AXI4(parameter.instructionFetchParameter)
  val out = Decoupled(new DecodeIO(parameter.iduParameter))
  val bpuUpdate = Input(Flipped(new BPUUpdate(parameter.bpuParameter)))
}

@instantiable
class Frontend(val parameter: CPUParameter)
    extends FixedIORawModule(new FrontendInterface(parameter))
    with SerializableModule[CPUParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val bpu: Instance[BPU] = Instantiate(new BPU(parameter.bpuParameter))
  val uncache: Instance[InstUncache] = Instantiate(new InstUncache(parameter.useAsyncReset, parameter.instructionFetchParameter, parameter.VAddrBits, parameter.DataBits))
  val ifu:  Instance[IFU] = Instantiate(new IFU(parameter))
  val ibuf: Instance[IBUF] = Instantiate(new IBUF(parameter.ibufParameter))
  val idu:  Instance[IDU] = Instantiate(new IDU(parameter.iduParameter))

  //==========================================================
  // BPU  IFU → IBUF → IDU
  //   ↓  ↑
  // Uncache
  //    ↓↑
  //   IMEM
  //==========================================================
  bpu.io.out <> uncache.io.req
  uncache.io.mem <> io.imem
  ifu.io.in <> uncache.io.resp
  PipelineConnect(ifu.io.out, ibuf.io.in, ibuf.io.out.fire, false.B)
  PipelineConnect(ibuf.io.out, idu.io.in, idu.io.out.fire, false.B)
  io.out :<>= idu.io.out

  uncache.io.flush := false.B
  bpu.io.flush := false.B
  bpu.io.update <> io.bpuUpdate

  // TODO: dirty
  bpu.io.clock := io.clock
  bpu.io.reset := io.reset
  ifu.io.clock := io.clock
  ifu.io.reset := io.reset
  ibuf.io.clock := io.clock
  ibuf.io.reset := io.reset
  idu.io.clock := io.clock
  idu.io.reset := io.reset
  uncache.io.clock := io.clock
  uncache.io.reset := io.reset
}