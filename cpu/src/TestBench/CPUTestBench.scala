package cpu.cpuemu

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.ltl.Property.{eventually, not}
import chisel3.ltl.{AssertProperty, CoverProperty, Delay, Sequence}
import chisel3.properties.{AnyClassType, Class, Property}
import chisel3.util.circt.dpi.{
  RawClockedNonVoidFunctionCall,
  RawClockedVoidFunctionCall,
  RawUnclockedNonVoidFunctionCall}
import chisel3.util._

import cpu._
import cpu.cpuemu.vip._

object CPUTestBenchParameter {
  implicit def rwP: upickle.default.ReadWriter[CPUTestBenchParameter] =
    upickle.default.macroRW
}

/** Parameter of [[CPU]]. */
case class CPUTestBenchParameter(
  testVerbatimParameter: TestVerbatimParameter,
  cpuParameter:          CPUParameter,
  timeout:               Int)
    extends SerializableModuleParameter {}

@instantiable
class CPUTestBenchOM(parameter: CPUTestBenchParameter) extends Class {
  val cpu = IO(Output(Property[AnyClassType]()))
  @public
  val cpuIn = IO(Input(Property[AnyClassType]()))
  cpu := cpuIn
}

class CPUTestBenchInterface(parameter: CPUTestBenchParameter) extends Bundle {
  val om = Output(Property[AnyClassType]())
}

object State extends ChiselEnum {
  val Running, GoodTrap, BadTrap, Timeout = Value
}

@instantiable
class CPUTestBench(val parameter: CPUTestBenchParameter)
    extends FixedIORawModule(new CPUTestBenchInterface(parameter))
    with SerializableModule[CPUTestBenchParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = verbatim.io.clock
  override protected def implicitReset: Reset = verbatim.io.reset
  layer.enable(layers.Verification)

  // Instantiate Drivers
  val verbatim: Instance[TestVerbatim] = Instantiate(
    new TestVerbatim(parameter.testVerbatimParameter)
  )
  // Instantiate DUT.
  val dut: Instance[CPU] = Instantiate(new CPU(parameter.cpuParameter))
  // Instantiate OM
  val omInstance = Instantiate(new CPUTestBenchOM(parameter))
  io.om := omInstance.getPropertyReference.asAnyClassType
  omInstance.cpuIn := dut.io.om

  // DUT
  dut.io.clock := implicitClock
  dut.io.reset := implicitReset
  dut.io.intr := 0.U

  // AXI4VIP
  val instructionFetchAXI = Module(
    new AXI4VIP(
      AXI4VIPParameter(
        name = "instructionFetchAXI",
        axiParameter = parameter.cpuParameter.instructionFetchParameter,
        outstanding = 4,
        readPayloadSize = 1,
        writePayloadSize = 1
      )
    )
  )
  instructionFetchAXI.io.clock := implicitClock
  instructionFetchAXI.io.reset := implicitReset
  instructionFetchAXI.io.channelId := 0.U

  val loadStoreAXI = Module(
    new AXI4VIP(
      AXI4VIPParameter(
        name = "loadStoreAXI",
        axiParameter = parameter.cpuParameter.loadStoreAXIParameter,
        outstanding = 4,
        readPayloadSize = 1,
        writePayloadSize = 1
      )
    )
  )
  loadStoreAXI.io.clock := implicitClock
  loadStoreAXI.io.reset := implicitReset
  loadStoreAXI.io.channelId := 1.U

  instructionFetchAXI.io.channel <> dut.io.imem
  loadStoreAXI.io.channel <> dut.io.dmem

  // Verification Logic
  val CPUProbe = probe.read(dut.io.cpuProbe)
  val retire = CPUProbe.retire.bits
  RawClockedVoidFunctionCall("retire_instruction")(
    implicitClock,
    CPUProbe.retire.valid,
    retire.inst,
    retire.pc,
    retire.gpr(0),retire.gpr(1),retire.gpr(2),retire.gpr(3),retire.gpr(4),retire.gpr(5),retire.gpr(6),retire.gpr(7),
    retire.gpr(8),retire.gpr(9),retire.gpr(10),retire.gpr(11),retire.gpr(12),retire.gpr(13),retire.gpr(14),retire.gpr(15),
    retire.gpr(16),retire.gpr(17),retire.gpr(18),retire.gpr(19),retire.gpr(20),retire.gpr(21),retire.gpr(22),retire.gpr(23),
    retire.gpr(24),retire.gpr(25),retire.gpr(26),retire.gpr(27),retire.gpr(28),retire.gpr(29),retire.gpr(30),retire.gpr(31),
    retire.csr(0),retire.csr(1),retire.csr(2),retire.csr(3),retire.csr(4),retire.csr(5),retire.csr(6),retire.csr(7),
    retire.csr(8),retire.csr(9),retire.csr(10),retire.csr(11),retire.csr(12),retire.csr(13),retire.csr(14),retire.csr(15),
    retire.csr(16),retire.csr(17),
    retire.skip,
    retire.is_rvc,
    retire.rfwen,
    retire.is_load,
    retire.is_store
  )

  // Simulation Logic
  val simulationTime: UInt = RegInit(0.U(64.W))
  simulationTime := simulationTime + 1.U
  val instructionCount: UInt = RegInit(0.U(64.W))
  when(CPUProbe.retire.valid) {
    instructionCount := instructionCount + 1.U
  }

  // For each timeout ticks, check it
  import State._
  val watchdogCode = RawClockedNonVoidFunctionCall("sim_watchdog", UInt(8.W))(implicitClock, true.B)
  when(watchdogCode =/= Running.asUInt) {
    RawClockedVoidFunctionCall(s"calculate_ipc")(
      implicitClock,
      when.cond,
      instructionCount,
      simulationTime
    )
    //stop(cf"""{"cycle":${simulationTime}, "instruction":${instructionCount}, "ipc":${ipc}}\n""")
    stop()
  }
}

object TestVerbatimParameter {
  implicit def rwP: upickle.default.ReadWriter[TestVerbatimParameter] =
    upickle.default.macroRW
}

case class TestVerbatimParameter(
  useAsyncReset:     Boolean,
  initFunctionName:  String,
  finalFunctionName: String,
  dumpFunctionName:  String,
  clockFlipTick:     Int,
  resetFlipTick:     Int)
    extends SerializableModuleParameter

@instantiable
class TestVerbatimOM(parameter: TestVerbatimParameter) extends Class {
  val useAsyncReset:     Property[Boolean] = IO(Output(Property[Boolean]()))
  val initFunctionName:  Property[String] = IO(Output(Property[String]()))
  val finalFunctionName: Property[String] = IO(Output(Property[String]()))
  val dumpFunctionName:  Property[String] = IO(Output(Property[String]()))
  val clockFlipTick:     Property[Int] = IO(Output(Property[Int]()))
  val resetFlipTick:     Property[Int] = IO(Output(Property[Int]()))
  val cpu = IO(Output(Property[AnyClassType]()))
  @public
  val cpuIn = IO(Input(Property[AnyClassType]()))
  cpu := cpuIn
  useAsyncReset := Property(parameter.useAsyncReset)
  initFunctionName := Property(parameter.initFunctionName)
  finalFunctionName := Property(parameter.finalFunctionName)
  dumpFunctionName := Property(parameter.dumpFunctionName)
  clockFlipTick := Property(parameter.clockFlipTick)
  resetFlipTick := Property(parameter.resetFlipTick)
}

/** Test blackbox for clockgen, wave dump and extra testbench-only codes. */
class TestVerbatimInterface(parameter: TestVerbatimParameter) extends Bundle {
  val clock: Clock = Output(Clock())
  val reset: Reset = Output(
    if (parameter.useAsyncReset) AsyncReset() else Bool()
  )
}

@instantiable
class TestVerbatim(parameter: TestVerbatimParameter)
    extends FixedIOExtModule(new TestVerbatimInterface(parameter))
    with HasExtModuleInline {
  setInline(
    s"$desiredName.sv",
    s"""module $desiredName(output reg clock, output reg reset);
       |  export "DPI-C" function ${parameter.dumpFunctionName};
       |  function ${parameter.dumpFunctionName}(input string file);
       |`ifdef VCS
       |    $$fsdbDumpfile(file);
       |    $$fsdbDumpvars("+all");
       |    $$fsdbDumpSVA;
       |    $$fsdbDumpon;
       |`endif
       |`ifdef VERILATOR
       |    $$dumpfile(file);
       |    $$dumpvars(0);
       |`endif
       |  endfunction;
       |
       |  import "DPI-C" context function void ${parameter.initFunctionName}();
       |  import "DPI-C" context function void ${parameter.finalFunctionName}();
       |  initial begin
       |    ${parameter.initFunctionName}();
       |    clock = 1'b0;
       |    reset = 1'b1;
       |  end
       |  final begin
       |    ${parameter.finalFunctionName}();
       |  end
       |  initial #(${parameter.resetFlipTick}) reset = 1'b0;
       |  always #${parameter.clockFlipTick} clock = ~clock;
       |endmodule
       |""".stripMargin
  )
}
