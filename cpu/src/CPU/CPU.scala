package cpu

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.properties.{AnyClassType, Class, Property}
import chisel3.util.{DecoupledIO, Valid}
import org.chipsalliance.rvdecoderdb.Instruction

import amba.axi4._
import cpu.frontend._
import cpu.backend._
import utility._

object CPUParameter {
  implicit def rwP: upickle.default.ReadWriter[CPUParameter] =
    upickle.default.macroRW
}

/** Parameter of [[CPU]] */
case class CPUParameter(extensions: Seq[String]) extends SerializableModuleParameter {
  val allInstructions: Seq[Instruction] = {
    org.chipsalliance.rvdecoderdb
      .instructions(org.chipsalliance.rvdecoderdb.extractResource(getClass.getClassLoader))
      .filter { instruction =>
        (
          extensions ++
            // Four mandatory instruction sets.
            Seq("rv_i", "rv_zicsr", "rv_zifencei", "rv_system")
        ).contains(instruction.instructionSet.name)
      }
  }.sortBy(_.instructionSet.name)
  private def hasInstructionSet(setName: String): Boolean =
    instructions.flatMap(_.instructionSets.map(_.name)).contains(setName)

  val decoderParam: DecoderParam = DecoderParam(allInstructions)

  def xLen: Int =
    (hasInstructionSet("rv32_i"), hasInstructionSet("rv64_i")) match {
      case (true, true)   => throw new Exception("cannot support both rv32 and rv64 together")
      case (true, false)  => 32
      case (false, true)  => 64
      case (false, false) => throw new Exception("no basic instruction found.")
    }

  def usingAtomics = hasInstructionSet("rv_a") || hasInstructionSet("rv64_a")
  def usingCompressed = hasInstructionSet("rv_c")

  /** paraemter for AXI4. */
  val instructionFetchParameter: AXI4BundleParameter = AXI4BundleParameter(
    addrWidth = XLEN,
    dataWidth = 64,
    idWidth = 1,
    isRO = true
  )

  val loadStoreAXI: AXI4BundleParameter = AXI4BundleParameter(
    addrWidth = XLEN,
    dataWidth = 64,
    idWidth = 1,
    isRO = false
  )

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

  val RegFileParameter: RegFileParameter = RegFileParameter(
    addrWidth = LogicRegsWidth,
    dataWidth = DataBits
  )

  val ScoreBoardParameter: ScoreBoardParameter = ScoreBoardParameter(
    addrWidth = LogicRegsWidth,
    dataWidth = DataBits
  )
}

/** Verification IO of [[CPU]] */
class Retire extends Bundle {
  val inst:     UInt = UInt(32.W)
  val pc:       UInt = UInt(64.W)
  val gpr:      Vec[UInt] = Vec(32, UInt(64.W))
  val mode:     UInt = UInt(64.W)
  val mstatus:  UInt = UInt(64.W)
  val sstatus:  UInt = UInt(64.W)
  val mepc:     UInt = UInt(64.W)
  val sepc:     UInt = UInt(64.W)
  val mtval:    UInt = UInt(64.W)
  val stval:    UInt = UInt(64.W)
  val mtvec:    UInt = UInt(64.W)
  val stvec:    UInt = UInt(64.W)
  val mcause:   UInt = UInt(64.W)
  val scause:   UInt = UInt(64.W)
  val satp:     UInt = UInt(64.W)
  val mip:      UInt = UInt(64.W)
  val mie:      UInt = UInt(64.W)
  val mscratch: UInt = UInt(64.W)
  val sscratch: UInt = UInt(64.W)
  val mideleg:  UInt = UInt(64.W)
  val medeleg:  UInt = UInt(64.W)
  val skip:     Bool = Bool()
  val is_rvc:   Bool = Bool()
  val rfwen:    Bool = Bool()
  val is_load:  Bool = Bool()
  val is_store: Bool = Bool()
}

class CPUProbe(parameter: CPUParameter) extends Bundle {
  val retire: Valid[Retire] = Valid(new Retire)
}

/** Metadata of [[CPU]]. */
@instantiable
class CPUOM(parameter: CPUParameter) extends Class {
  val extensions: Property[Seq[String]] = IO(Output(Property[Seq[String]]()))
  extensions := Property(parameter.extensions)
}

/** Interface of [[CPU]]. */
class CPUInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val imem = new AXI4ROIrrevocable(parameter.instructionFetchParameter)
  val dmem = new AXI4RWIrrevocable(parameter.loadStoreAXI)
  val intr = Input(UInt(paraemter.NrExtIntr.W))
  val probe = Output(Probe(new CPUProbe(parameter), layers.Verification))
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

  val frontend: Instance[Frontend] = Instantiate(new Frontend(parameter))
  val backend:  Instance[Backend] = Instantiate(new Backend(parameter))
  PipelineConnect(frontend.io.out, backend.io.in, false.B, false.B)

  // Assign Probe
  val probeWire: CPUProbe = Wire(new CPUProbe(parameter))
  define(io.probe, ProbeValue(probeWire))
  probeWire.retire := backend.io.probe.retire

  // Assign Metadata
  val omInstance: Instance[CPUOM] = Instantiate(new CPUOM(parameter))
  io.om := omInstance.getPropertyReference.asAnyClassType
}
