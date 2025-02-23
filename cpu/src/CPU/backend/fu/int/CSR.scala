package cpu.backend.fu

import chisel3._
import chisel3.util._
import chisel3.stage._
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import cpu._
import utility._

class CSRProbe(parameter: CPUParameter) extends Bundle {
  val csr: Vec[UInt] = Vec(18, UInt(64.W))
}

class CSRInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  // val src = Vec(2, Input(UInt(parameter.XLEN.W)))
  // val func = Input(FuOpType())
  // val result = Output(UInt(parameter.XLEN.W))
  val probe = Output(Probe(new CSRProbe(parameter), layers.Verification))
}

class CSR(val parameter: CPUParameter)
    extends FixedIORawModule(new CSRInterface(parameter))
    with SerializableModule[CPUParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset
  val XLEN = parameter.XLEN

  val mode = RegInit(UInt(XLEN.W), 3.U)
  val mstatus = RegInit(UInt(XLEN.W), "ha00000000".U)
  val mcause = RegInit(UInt(XLEN.W), 0.U)
  val mepc = RegInit(UInt(XLEN.W), 0.U)
  val sstatus = RegInit(UInt(XLEN.W), "h200000000".U)
  val scause = RegInit(UInt(XLEN.W), 0.U)
  val sepc = RegInit(UInt(XLEN.W), 0.U)
  val satp = RegInit(UInt(XLEN.W), 0.U)

  val mip = RegInit(UInt(XLEN.W), 0.U)
  val mie = RegInit(UInt(XLEN.W), 0.U)
  val mscratch = RegInit(UInt(XLEN.W), 0.U)
  val sscratch = RegInit(UInt(XLEN.W), 0.U)
  val mideleg = RegInit(UInt(XLEN.W), 0.U)
  val medeleg = RegInit(UInt(XLEN.W), 0.U)

  val mtval = RegInit(UInt(XLEN.W), 0.U)
  val stval = RegInit(UInt(XLEN.W), 0.U)
  val mtvec = RegInit(UInt(XLEN.W), 0.U)
  val stvec = RegInit(UInt(XLEN.W), 0.U)
  // val mvendorid  = RegInit(UInt(XLEN.W), 0.U)
  // val marchid    = RegInit(UInt(XLEN.W), 0.U)
  // val mhartid    = RegInit(UInt(XLEN.W), 0.U)
  layer.block(layers.Verification) {
    val csr = Wire(Vec(18, UInt(64.W)))
    csr(0) := mode
    csr(1) := mstatus
    csr(2) := sstatus
    csr(3) := mepc
    csr(4) := sepc
    csr(5) := mtval
    csr(6) := stval
    csr(7) := mtvec
    csr(8) := stvec
    csr(9) := mcause
    csr(10) := scause
    csr(11) := satp
    csr(12) := mip
    csr(13) := mie
    csr(14) := mscratch
    csr(15) := sscratch
    csr(16) := mideleg
    csr(17) := medeleg

    val probeWire: CSRProbe = Wire(new CSRProbe(parameter))
    define(io.probe, ProbeValue(probeWire))
    probeWire.csr := csr
  }

}
