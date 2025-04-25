package cpu

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

object CPUParameter {
  implicit def rwP: upickle.default.ReadWriter[CPUParameter] =
    upickle.default.macroRW
}

/** Parameter of [[CPU]] */
case class CPUParameter(useAsyncReset: Boolean, extensions: Seq[String]) extends SerializableModuleParameter {
  val instructions: Seq[Instruction] = {
    org.chipsalliance.rvdecoderdb
      .instructions(org.chipsalliance.rvdecoderdb.extractResource(getClass.getClassLoader))
      .filter { instruction =>
        (
          extensions ++
            // Four mandatory instruction sets.
            Seq("rv_i", "rv_zicsr", "rv_zifencei", "rv_system")
        ).contains(instruction.instructionSet.name)
      }
  }.toSeq.filter {
    // special case for rv32 pseudo from rv64
    case i if i.pseudoFrom.isDefined && Seq("slli", "srli", "srai").contains(i.name) => true
    case i if i.pseudoFrom.isDefined                                                 => false
    case _                                                                           => true
  }
    .sortBy(_.instructionSet.name)

  private def hasInstructionSet(setName: String): Boolean =
    instructions.flatMap(_.instructionSets.map(_.name)).contains(setName)

  def XLEN: Int =
    (hasInstructionSet("rv32_i"), hasInstructionSet("rv64_i")) match {
      case (true, true)   => throw new Exception("cannot support both rv32 and rv64 together")
      case (true, false)  => 32
      case (false, true)  => 64
      case (false, false) => throw new Exception("no basic instruction found.")
    }

  def usingAtomics = hasInstructionSet("rv_a") || hasInstructionSet("rv64_a")
  def usingCompressed = hasInstructionSet("rv_c")

  val ResetVector: Long = 0x80000000L

  // TODO: support external interrupt
  val NrExtIntr: Int = 0

  val HasDTLB:   Boolean = false
  val HasITLB:   Boolean = false
  val HasDcache: Boolean = false
  val HasIcache: Boolean = false
  val MmodeOnly: Boolean = false

  val VAddrBits:      Int = 39
  val PAddrBits:      Int = 32
  val DataBits:       Int = XLEN
  val DataBytes:      Int = DataBits / 8
  val AXI4SIZE:       Int = log2Up(DataBytes)
  val NrPhyRegs:      Int = 32
  val LogicRegsWidth: Int = log2Up(NrPhyRegs)
  val NumSrc:         Int = 2
  val PredictWidth:   Int = 4

  val instructionFetchParameter: AXI4BundleParameter = AXI4BundleParameter(
    addrWidth = PAddrBits,
    dataWidth = DataBits,
    idWidth = 1,
    isRO = true
  )

  val loadStoreAXIParameter: AXI4BundleParameter = AXI4BundleParameter(
    addrWidth = PAddrBits,
    dataWidth = DataBits,
    idWidth = 1,
    isRO = false
  )
  val regfileParameter: RegFileParameter = RegFileParameter(
    addrWidth = LogicRegsWidth,
    dataWidth = DataBits,
    nrReg = NrPhyRegs,
    useAsyncReset = useAsyncReset
  )

  val scoreboardParameter: ScoreBoardParameter = ScoreBoardParameter(
    regNum = NrPhyRegs,
    addrWidth = LogicRegsWidth,
    dataWidth = DataBits,
    numSrc = NumSrc,
    useAsyncReset = useAsyncReset
  )

  val iduParameter: IDUParameter = IDUParameter(
    addrBits = VAddrBits,
    numSrc = NumSrc,
    regsWidth = LogicRegsWidth,
    xlen = XLEN,
    decoderParam = DecoderParam(instructions),
    useAsyncReset = useAsyncReset
  )

  val ibufParameter: IBUFParameter = IBUFParameter(
    vaddrBits = VAddrBits,
    useAsyncReset = useAsyncReset,
    xlen = XLEN
  )

  val bpuParameter: BPUParameter = BPUParameter(
    NRbtb = 512,
    NRras = 16,
    fetchWidth = 8,
    useCompressed = usingCompressed,
    useAsyncReset = useAsyncReset,
    vaddrBits = VAddrBits
  )
}

/** Verification IO of [[CPU]] */
class Retire extends Bundle {
  val inst:     UInt = UInt(32.W)
  val pc:       UInt = UInt(64.W)
  val gpr:      Vec[UInt] = Vec(32, UInt(64.W))
  val csr:      Vec[UInt] = Vec(18, UInt(64.W))
  val skip:     Bool = Bool()
  val is_rvc:   Bool = Bool()
  val rfwen:    Bool = Bool()
  val is_load:  Bool = Bool()
  val is_store: Bool = Bool()
}

class CPUProbe(parameter: CPUParameter) extends Bundle {
  // val backendProbe: BackendProbe = new BackendProbe(parameter)
  val retire: Valid[Retire] = Valid(new Retire)
}

/** Metadata of [[CPU]]. */
@instantiable
class CPUOM(parameter: CPUParameter) extends Class {
  val useAsyncReset: Property[Boolean] = IO(Output(Property[Boolean]()))
  val extensions:    Property[Seq[String]] = IO(Output(Property[Seq[String]]()))
  useAsyncReset := Property(parameter.useAsyncReset)
  extensions := Property(parameter.extensions)
}

/** Interface of [[CPU]]. */
class CPUInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val imem = AXI4(parameter.instructionFetchParameter)
  val dmem = AXI4(parameter.loadStoreAXIParameter)
  val intr = Input(UInt(parameter.NrExtIntr.W))
  val cpuProbe = Output(Probe(new CPUProbe(parameter), layers.Verification))
  val om = Output(Property[AnyClassType]())
}

/** Hardware Implementation of CPU */
@instantiable
class CPU(val parameter: CPUParameter)
    extends FixedIORawModule(new CPUInterface(parameter))
    with SerializableModule[CPUParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  // val bpu: Instance[BPU] = Instantiate(new BPU(parameter.bpuParameter))
  val instUncache: Instance[InstUncache] = Instantiate(
    new InstUncache(
      parameter.useAsyncReset,
      parameter.instructionFetchParameter,
      parameter.VAddrBits,
      parameter.DataBits
    )
  )
  val ifu:  Instance[IFU] = Instantiate(new IFU(parameter))
  val ibuf: Instance[IBUF] = Instantiate(new IBUF(parameter.ibufParameter))
  val idu:  Instance[IDU] = Instantiate(new IDU(parameter.iduParameter))
  val isu:  Instance[ISU] = Instantiate(new ISU(parameter))
  val exu:  Instance[EXU] = Instantiate(new EXU(parameter))
  val wbu:  Instance[WBU] = Instantiate(new WBU(parameter))

  val flush0 = ibuf.io.redirect.valid
  val flush1 = wbu.io.redirect.valid

  // ==========================================================
  //  IFU → IBUF → IDU → ISU → EXU → WBU
  //  ↓↑                       ↓↑
  // instUncache              dataUncache
  //    ↓↑                         ↓↑
  //   imem                       dmem
  // ==========================================================

  // bpu.io.out <> instUncache.io.req
  ifu.io.imem <> instUncache.io.in
  instUncache.io.out <> io.imem
  ibuf.io.in <> ifu.io.out
  PipelineConnect(ibuf.io.out, idu.io.in, idu.io.out.fire, flush1)
  PipelineConnect(idu.io.out, isu.io.in, isu.io.out.fire, flush1)
  PipelineConnect(isu.io.out, exu.io.in, exu.io.out.fire, flush1)
  PipelineConnect(exu.io.out, wbu.io.in, true.B, flush1)

  ifu.io.bpuUpdate <> exu.io.bpuUpdate
  ifu.io.redirect.target := Mux(wbu.io.redirect.valid, wbu.io.redirect.target, ibuf.io.redirect.target)
  ifu.io.redirect.valid := wbu.io.redirect.valid || ibuf.io.redirect.valid

  instUncache.io.flush := flush0 || flush1
  ibuf.io.flush := flush1
  isu.io.flush := flush1
  exu.io.flush := flush1

  // bypass
  isu.io.forward <> exu.io.forward
  isu.io.wb <> wbu.io.rfwrite
  exu.io.redirect_pc := isu.io.out.bits.pc // 检查下一个pc是否正确

  val regfile = Instantiate(new RegFile(parameter.regfileParameter))
  regfile.io.readPorts <> isu.io.rfread
  regfile.io.writePorts <> wbu.io.rfwrite

  // val scoreboard = Instantiate(new ScoreBoard(parameter.scoreboardParameter))
  // scoreboard.io.isu <> isu.io.scoreboard
  // scoreboard.io.wb <> wbu.io.scoreboard
  // scoreboard.io.flush := flush1

  // val dataUncache = Instantiate(new DataUncache(parameter))
  // dataUncache.io.flush := flush1
  // dataUncache.io.load <> exu.io.load
  // dataUncache.io.store <> exu.io.store
  // io.dmem <> dataUncache.io.out
  val fence = false.B
  val empty = Wire(UInt(1.W))
  io.dmem <> Cache(io.clock, io.reset, "b00".U, fence, exu.io.dmem, io.dmem, empty)

  layer.block(layers.Verification) {
    val probeWire: CPUProbe = Wire(new CPUProbe(parameter))
    define(io.cpuProbe, ProbeValue(probeWire))
    probeWire.retire.valid := RegNext(wbu.io.in.fire)
    probeWire.retire.bits.inst := RegNext(wbu.io.in.bits.instr)
    probeWire.retire.bits.pc := RegNext(wbu.io.in.bits.pc)
    probeWire.retire.bits.gpr := probe.read(regfile.io.probe).gpr
    probeWire.retire.bits.csr := RegNext(probe.read(exu.io.probe).csrprobe.csr)
    probeWire.retire.bits.skip := RegNext(wbu.io.in.bits.skip)
    probeWire.retire.bits.is_rvc := RegNext(wbu.io.in.bits.isRVC)
    probeWire.retire.bits.rfwen := RegNext(wbu.io.rfwrite(0).wen)
    probeWire.retire.bits.is_load := RegNext(wbu.io.in.bits.is_load)
    probeWire.retire.bits.is_store := RegNext(wbu.io.in.bits.is_store)
  }

  // TODO: dirty
  ifu.io.clock := io.clock
  ifu.io.reset := io.reset
  ibuf.io.clock := io.clock
  ibuf.io.reset := io.reset
  idu.io.clock := io.clock
  idu.io.reset := io.reset
  instUncache.io.clock := io.clock
  instUncache.io.reset := io.reset
  isu.io.clock := io.clock
  isu.io.reset := io.reset
  exu.io.clock := io.clock
  exu.io.reset := io.reset
  wbu.io.clock := io.clock
  wbu.io.reset := io.reset
  regfile.io.clock := io.clock
  regfile.io.reset := io.reset
  // scoreboard.io.clock := io.clock
  // scoreboard.io.reset := io.reset
  dataUncache.io.clock := io.clock
  dataUncache.io.reset := io.reset

  // Assign Metadata
  val omInstance: Instance[CPUOM] = Instantiate(new CPUOM(parameter))
  io.om := omInstance.getPropertyReference.asAnyClassType
}
