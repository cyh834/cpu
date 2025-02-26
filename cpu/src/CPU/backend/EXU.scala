package cpu.backend

import chisel3._
import chisel3.util._
import chisel3.stage._
import chisel3.util.experimental.BoringUtils
import chisel3.probe.{define, Probe, ProbeValue}
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

  // debug
  val instr = UInt(32.W)
  val pc = UInt(parameter.VAddrBits.W)
  val isRVC = Bool()
  val skip = Bool()
  val is_load = Bool()
  val is_store = Bool()
}

class EXUProbe(parameter: CPUParameter) extends Bundle {
  val csrprobe = new CSRProbe(parameter)
}

class EXUInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val in = Flipped(Decoupled(new DecodeIO(parameter.iduParameter)))
  val out = Decoupled(new WriteBackIO(parameter))
  val flush = Input(Bool())
  val forward = new ForwardIO(parameter.LogicRegsWidth, parameter.XLEN)
  // val dmem = new AXI4RWIrrevocable(parameter.loadStoreAXIParameter)
  val load = new LoadInterface(parameter)
  val store = new StoreInterface(parameter)
  val bpuUpdate = Output(new BPUUpdate(parameter.bpuParameter))
  val redirect_pc = Input(UInt(parameter.VAddrBits.W))

  val probe = Output(Probe(new EXUProbe(parameter), layers.Verification))
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

  val s_idle :: s_busy :: Nil = Enum(2)
  val state = RegInit(s_idle)

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

  val lsu = Instantiate(new LSU(parameter)).io
  val islsu = FuType.islsu(fuType)
  lsu.clock := io.clock
  lsu.reset := io.reset
  lsu.src := io.in.bits.src
  lsu.imm := io.in.bits.imm
  lsu.func := fuOpType
  lsu.isStore := FuType.isstu(fuType)
  lsu.valid := (state === s_idle) && islsu && !io.flush && io.in.valid
  io.load <> lsu.load
  io.store <> lsu.store

  val mdu = Instantiate(new MDU(parameter)).io
  val ismdu = FuType.ismdu(fuType)
  mdu.clock := io.clock
  mdu.reset := io.reset
  mdu.flush := io.flush
  mdu.in.bits.src := io.in.bits.src
  mdu.in.bits.func := fuOpType
  mdu.in.valid := (state === s_idle) && ismdu && !io.flush && io.in.valid

  val csr = Instantiate(new CSR(parameter)).io
  csr.clock := io.clock
  csr.reset := io.reset

  when(lsu.valid || mdu.in.valid) {
    state := s_busy
  }
  when(io.out.fire || io.flush) {
    state := s_idle
  }

  io.out.valid := MuxCase(
    io.in.valid,
    Array(
      islsu -> lsu.out_valid,
      ismdu -> mdu.out.valid
    )
  )

  io.in.ready := io.out.fire || !io.in.valid

  // target
  val isJmp = FuType.isjmp(fuType)
  val isBrh = FuType.isbrh(fuType)
  val target = MuxCase(
    io.in.bits.pc + 4.U,
    Array(
      (isJmp && !jmp.isAuipc) -> jmp.target,
      (isBrh && brh.taken) -> brh.target,
      io.in.bits.isRVC -> (io.in.bits.pc + 2.U)
    )
  )
  val mistarget = (io.redirect_pc =/= target) && io.in.valid

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
  io.bpuUpdate.btb.bits.isRVC := io.in.bits.isRVC
  io.bpuUpdate.btb.valid := mistarget

  io.bpuUpdate.ras.bits.brtype := brtype
  io.bpuUpdate.ras.bits.isRVC := io.in.bits.isRVC
  io.bpuUpdate.ras.valid := Brtype.isRas(brtype) & io.out.fire

  io.out.bits.wb.wen := io.in.bits.rfWen
  io.out.bits.wb.addr := io.in.bits.ldest
  io.out.bits.wb.data := MuxCase(
    alu.result,
    Array(
      isJmp -> jmp.result,
      islsu -> lsu.result,
      ismdu -> mdu.out.bits.result
    )
  )

  // test
  io.out.bits.instr := io.in.bits.instr
  io.out.bits.isRVC := io.in.bits.isRVC
  io.out.bits.pc := io.in.bits.pc
  io.out.bits.is_load := FuType.isldu(fuType)
  io.out.bits.is_store := FuType.isstu(fuType)
  val addr = io.in.bits.src(0) + io.in.bits.imm
  io.out.bits.skip := islsu && (addr >= 0x40600000.U && addr < 0x40600010.U) // Uart

  layer.block(layers.Verification) {
    val probeWire: EXUProbe = Wire(new EXUProbe(parameter))
    define(io.probe, ProbeValue(probeWire))
    probeWire.csrprobe.csr := probe.read(csr.probe).csr
  }
}
