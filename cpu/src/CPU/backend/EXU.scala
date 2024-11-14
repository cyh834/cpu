package cpu.backend

import chisel3._
import chisel3.util._
import chisel3.stage._
import chisel3.util.experimental.BoringUtils
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}

import utility._
import cpu._
import cpu.frontend._
import cpu.backend.fu._
import amba.axi4._

class WriteBackIO(parameter: CPUParameter) extends Bundle {
  val wb = new RfWritePort(parameter.regfileParameter)
  val redirect = new RedirectIO(parameter.VAddrBits)

  //debug
  val instr = UInt(32.W)
  val pc = UInt(parameter.VAddrBits.W)
  val isRVC = Bool()
}

class EXUInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val in = Flipped(Decoupled(new DecodeIO(parameter.iduParameter)))
  val out = Decoupled(new WriteBackIO(parameter))
  val flush = Input(Bool())
  val forward = new ForwardIO(parameter.LogicRegsWidth, parameter.XLEN)
  val dmem = new AXI4RWIrrevocable(parameter.loadStoreAXIParameter)
  val bpuUpdate = Output(new BPUUpdate(parameter.bpuParameter))
}

@instantiable
class EXU(val parameter: CPUParameter)
    extends FixedIORawModule(new EXUInterface(parameter))
    with SerializableModule[CPUParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val (fuType, fuOpType, brtype): (UInt, UInt, UInt) = (io.in.bits.fuType, io.in.bits.fuOpType, io.in.bits.brtype)

  io.dmem := DontCare
  // todo: 用循环实现?
  val alu = Instantiate(new ALU(parameter)).io
  alu.clock := io.clock
  alu.reset := io.reset
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
  io.bpuUpdate.pht.bits.pc := io.in.bits.pc
  io.bpuUpdate.pht.bits.taken := brh.taken
  io.bpuUpdate.pht.valid := isBrh & io.out.fire

  io.bpuUpdate.btb.bits.pc := io.in.bits.pc
  io.bpuUpdate.btb.bits.target := target
  io.bpuUpdate.btb.bits.brtype := brtype
  io.bpuUpdate.btb.valid := isJmp || isBrh

  io.bpuUpdate.ras.bits.brtype := brtype
  io.bpuUpdate.ras.bits.isRVC := io.in.bits.isRVC
  io.bpuUpdate.ras.valid := Brtype.isRas(brtype)

  io.out.bits.instr := io.in.bits.instr
  io.out.bits.isRVC := io.in.bits.isRVC
  io.out.bits.pc := io.in.bits.pc
}
