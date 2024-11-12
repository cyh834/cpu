package cpu.backend

import chisel3._
import chisel3.util._
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.stage._
import chisel3.util.experimental.BoringUtils
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}

import utility._
import cpu._
import cpu.frontend._
import cpu.backend.fu._
import amba.axi4._

class ExuProbe(parameter: CPUParameter) extends Bundle {
  val instr = UInt(32.W)
  val pc = UInt(parameter.VAddrBits.W)
  val isRVC = Bool()
}

class WriteBackIO(parameter: CPUParameter) extends Bundle {
  val wb = new RfWritePort(parameter.regfileParameter)
  val redirect = new RedirectIO(parameter.VAddrBits)
  val probe = Output(Probe(new ExuProbe(parameter), layers.Verification))
}

class EXUInterface(parameter: CPUParameter) extends Bundle {
  val in = Flipped(Decoupled(new DecodeIO(parameter.iduParameter)))
  val out = Decoupled(new WriteBackIO(parameter))
  val flush = Input(Bool())
  val forward = new ForwardIO(parameter.LogicRegsWidth, parameter.XLEN)
  val dmem = new AXI4RWIrrevocable(parameter.loadStoreAXIParameter)
}

class EXU(val parameter: CPUParameter)
    extends FixedIORawModule(new EXUInterface(parameter))
    with SerializableModule[CPUParameter] {

  val (fuType, fuOpType, brtype): (UInt, UInt, UInt) = (io.in.bits.fuType, io.in.bits.fuOpType, io.in.bits.brtype)

  // todo: 用循环实现?
  val alu = Instantiate(new ALU(parameter)).io
  alu.src := io.in.bits.src
  alu.func := fuOpType

  val brh = Instantiate(new BRH(parameter)).io
  brh.src := io.in.bits.src
  brh.func := fuOpType
  brh.pred_taken := io.in.bits.pred_taken
  brh.pc := io.in.bits.pc
  brh.offset := io.in.bits.imm

  val jmp = Instantiate(new JMP(parameter)).io
  jmp.src := io.in.bits.src
  jmp.pc := io.in.bits.pc
  jmp.func := fuOpType
  jmp.isRVC := io.in.bits.isRVC

  io.out.bits.wb.wen := io.in.bits.rfWen
  io.out.bits.wb.addr := io.in.bits.ldest
  io.out.bits.wb.data := MuxLookup(fuType, alu.result)(
    Seq(
      FuType.jmp -> jmp.result
    )
  )

  io.out.valid := io.in.valid && !io.flush
  io.in.ready := io.out.ready

  // target
  val isJmp = FuType.isjmp(fuType)
  val isBrh = FuType.isbrh(fuType)
  val mistarget = MuxCase(
    false.B,
    Array(
      isJmp -> jmp.mistarget,
      isBrh -> brh.mispredict
    )
  )
  val target = MuxCase(
    0.U,
    Array(
      isJmp -> jmp.target,
      isBrh -> brh.target
    )
  )
  io.out.bits.redirect.target := target
  io.out.bits.redirect.valid := mistarget
  // io.out.bits.redirect.realtaken := brh.taken

  // forward
  io.forward.rfDest := io.in.bits.ldest
  io.forward.rfData := io.out.bits.wb.data
  io.forward.valid := io.out.valid & io.in.bits.rfWen

  // update bpu
  val rasupdate = WireInit(0.U.asTypeOf(Valid(new RASUpdate(parameter.BPUParameter))))
  BoringUtils.addSink(rasupdate, "rasupdate")
  val btbupdate = WireInit(0.U.asTypeOf(Valid(new BTBUpdate(parameter.BPUParameter))))
  BoringUtils.addSink(btbupdate, "btbupdate")
  val phtupdate = WireInit(0.U.asTypeOf(Valid(new PHTUpdate(parameter.BPUParameter))))
  BoringUtils.addSink(phtupdate, "phtupdate")

  phtupdate.bits.pc := io.in.bits.pc
  phtupdate.bits.taken := brh.taken
  phtupdate.valid := isBrh & io.out.fire

  btbupdate.bits.pc := io.in.bits.pc
  btbupdate.bits.target := target
  btbupdate.bits.brtype := brtype
  btbupdate.valid := isJmp || isBrh

  rasupdate.bits.brtype := brtype
  rasupdate.bits.isRVC := io.in.bits.isRVC
  rasupdate.valid := Brtype.isRas(brtype)

  // probe
  val probeWire: ExuProbe = Wire(new ExuProbe(parameter))
  define(io.out.bits.probe, ProbeValue(probeWire))
  probeWire.instr := io.in.bits.probe.instr
  probeWire.isRVC := io.in.bits.isRVC
  probeWire.pc := io.in.bits.pc
}
