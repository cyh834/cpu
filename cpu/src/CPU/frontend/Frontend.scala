package cpu.frontend

import chisel3._
import chisel3.util._

import utility._
import amba.axi4._
import cpu._
import cpu.cache.InstrUncache


class FrontendInterface(parameter: CPUParameter) extends Bundle{
    val imem = new AXI4ROIrrevocable(parameter.instructionFetchParameter)
    val out = Decoupled(new DecodeIO)
}

@instantiable
class Frontend(val parameter: CPUParameter) 
    extends FixedIORawModule(new FrontendInterface(parameter))
    with SerializableModule[CPUParameter]{

    val ifu: Instance[IFU] = Instantiate(new IFU(parameter))
    val ibuf: Instance[IBUF] = Instantiate(new IBUF(parameter))
    val idu: Instance[IDU] = Instantiate(new IDU(parameter))

    PipelineConnect(ifu.io.out, ibuf.io.in, ibuf.io.out.fire, false.B)
    PipelineConnect(ibuf.io.out, idu.io.in, idu.io.out.fire, false.B)

    io.out <> idu.io.out

    val uncache: Instance[InstrUncache] = Instantiate(new InstrUncache(paraemter))
    uncache.io.flush := false.B
    uncache.io.ifu <> ifu.io.imem
    io.imem <> uncache.io.mem
}