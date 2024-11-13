package cpu.frontend

import chisel3._
import chisel3.util._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}

import utility._
import amba.axi4._
import cpu._
import cpu.cache.InstrUncache

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

  val ifu:  Instance[IFU] = Instantiate(new IFU(parameter))
  val ibuf: Instance[IBUF] = Instantiate(new IBUF(parameter.ibufParameter))
  val idu:  Instance[IDU] = Instantiate(new IDU(parameter.iduParameter))

  PipelineConnect(ifu.io.out, ibuf.io.in, ibuf.io.out.fire, false.B)
  PipelineConnect(ibuf.io.out, idu.io.in, idu.io.out.fire, false.B)

  io.out :<>= idu.io.out

  ifu.io.bpuUpdate := io.bpuUpdate

  val uncache: Instance[InstrUncache] = Instantiate(
    new InstrUncache(parameter.useAsyncReset, parameter.instructionFetchParameter)
  )
  uncache.io.flush := false.B
  uncache.io.ifu <> ifu.io.imem
  io.imem <> uncache.io.mem
}
