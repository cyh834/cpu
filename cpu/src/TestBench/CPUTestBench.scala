//package cpu.cpuemu
//
//import chisel3._
//import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
//import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
//import chisel3.ltl.Property.{eventually, not}
//import chisel3.ltl.{AssertProperty, CoverProperty, Delay, Sequence}
//import chisel3.properties.{AnyClassType, Class, Property}
//import chisel3.util.circt.dpi.{RawClockedNonVoidFunctionCall, RawUnclockedNonVoidFunctionCall}
//import chisel3.util._
//
//import cpu._
//import cpu.cpuemu.vip._
//
//object CPUTestBenchParameter {
//  implicit def rwP: upickle.default.ReadWriter[CPUTestBenchParameter] =
//    upickle.default.macroRW
//}
//
///** Parameter of [[CPU]]. */
//case class CPUTestBenchParameter(
//  testVerbatimParameter: TestVerbatimParameter,
//  cpuParameter:          CPUParameter,
//  timeout:               Int)
//    extends SerializableModuleParameter {}
//
//@instantiable
//class CPUTestBenchOM(parameter: CPUTestBenchParameter) extends Class {
//  val cpu = IO(Output(Property[AnyClassType]()))
//  @public
//  val cpuIn = IO(Input(Property[AnyClassType]()))
//  cpu := cpuIn
//}
//
//class CPUTestBenchInterface(parameter: CPUTestBenchParameter) extends Bundle {
//  val om = Output(Property[AnyClassType]())
//}
//
//object State extends ChiselEnum {
//  val GoodTrap, BadTrap, Timeout, Running = Value
//}
//
//@instantiable
//class CPUTestBench(val parameter: CPUTestBenchParameter)
//    extends FixedIORawModule(new CPUTestBenchInterface(parameter))
//    with SerializableModule[CPUTestBenchParameter]
//    with ImplicitClock
//    with ImplicitReset {
//  override protected def implicitClock: Clock = verbatim.io.clock
//  override protected def implicitReset: Reset = verbatim.io.reset
//  // Instantiate Drivers
//  val verbatim: Instance[TestVerbatim] = Instantiate(
//    new TestVerbatim(parameter.testVerbatimParameter)
//  )
//  // Instantiate DUT.
//  val dut: Instance[CPU] = Instantiate(new CPU(parameter.cpuParameter))
//  // Instantiate OM
//  val omInstance = Instantiate(new CPUTestBenchOM(parameter))
//  io.om := omInstance.getPropertyReference.asAnyClassType
//  omInstance.cpuIn := dut.io.om
//
//  dut.io.clock := implicitClock
//  dut.io.reset := implicitReset
//
//  // Simulation Logic
//  val simulationTime: UInt = RegInit(0.U(64.W))
//  simulationTime := simulationTime + 1.U
//  // For each timeout ticks, check it
//  val (_, callWatchdog) = Counter(true.B, parameter.timeout / 2)
//  // should be in sync with the enum in driver.rs
//  import State._
//  val watchdogCode = RawUnclockedNonVoidFunctionCall("cpu_watchdog", UInt(8.W))(callWatchdog)
//  when(watchdogCode =/= Running.asUInt) {
//    stop(cf"""{"event":"SimulationStop","reason": ${watchdogCode},"cycle":${simulationTime}}\n""")
//  }
//
//  val axi4vip0 = new AXI4VIP(parameter.cpuParameter.instructionFetchParameter)
//  axi4vip0.clock := implicitClock
//  axi4vip0.reset := implicitReset
//  val axi4vip1 = new AXI4VIP(parameter.cpuParameter.loadStoreAXIParameter)
//  axi4vip1.clock := implicitClock
//  axi4vip1.reset := implicitReset
//
//  dut.io.imem <> axi4vip0.io
//  dut.io.dmem <> axi4vip1.io
//
//  val CPUProbe = probe.read(dut.io.probe)
//  RawClockedVoidFunctionCall("retire_instruction")(clock, CPUProbe.retire.valid, CPUProbe.retire.bits)
//
//}
//
//object TestVerbatimParameter {
//  implicit def rwP: upickle.default.ReadWriter[TestVerbatimParameter] =
//    upickle.default.macroRW
//}
//
//case class TestVerbatimParameter(
//  useAsyncReset:    Boolean,
//  initFunctionName:  String,
//  finalFunctionName: String,
//  dumpFunctionName:  String,
//  clockFlipTick:     Int,
//  resetFlipTick:     Int)
//    extends SerializableModuleParameter
//
//@instantiable
//class TestVerbatimOM(parameter: TestVerbatimParameter) extends Class {
//  val useAsyncReset:    Property[Boolean] = IO(Output(Property[Boolean]()))
//  val initFunctionName:  Property[String] = IO(Output(Property[String]()))
//  val finalFunctionName: Property[String] = IO(Output(Property[String]()))
//  val dumpFunctionName:  Property[String] = IO(Output(Property[String]()))
//  val clockFlipTick:     Property[Int] = IO(Output(Property[Int]()))
//  val resetFlipTick:     Property[Int] = IO(Output(Property[Int]()))
//  val cpu = IO(Output(Property[AnyClassType]()))
//  @public
//  val cpuIn = IO(Input(Property[AnyClassType]()))
//  cpu := cpuIn
//  useAsyncReset    := Property(parameter.useAsyncReset)
//  initFunctionName := Property(parameter.initFunctionName)
//  finalFunctionName := Property(parameter.finalFunctionName)
//  dumpFunctionName := Property(parameter.dumpFunctionName)
//  clockFlipTick := Property(parameter.clockFlipTick)
//  resetFlipTick := Property(parameter.resetFlipTick)
//}
//
///** Test blackbox for clockgen, wave dump and extra testbench-only codes. */
//class TestVerbatimInterface(parameter: TestVerbatimParameter) extends Bundle {
//  val clock: Clock = Output(Clock())
//  val reset: Reset = Output( 
//      if (parameter.useAsyncReset) AsyncReset() else Bool() 
//    )
//}
//
//@instantiable
//class TestVerbatim(parameter: TestVerbatimParameter)
//    extends FixedIOExtModule(new TestVerbatimInterface(parameter))
//    with HasExtModuleInline {
//  setInline(
//    s"$desiredName.sv",
//    s"""module $desiredName(output reg clock, output reg reset);
//       |  export "DPI-C" function ${parameter.dumpFunctionName};
//       |  function ${parameter.dumpFunctionName}(input string file);
//       |`ifdef VCS
//       |    $$fsdbDumpfile(file);
//       |    $$fsdbDumpvars("+all");
//       |    $$fsdbDumpSVA;
//       |    $$fsdbDumpon;
//       |`endif
//       |`ifdef VERILATOR
//       |    $$dumpfile(file);
//       |    $$dumpvars(0);
//       |`endif
//       |  endfunction;
//       |
//       |  import "DPI-C" context function void ${parameter.initFunctionName}();
//       |  import "DPI-C" context function void ${parameter.finalFunctionName}();
//       |  initial begin
//       |    ${parameter.initFunctionName}();
//       |    clock = 1'b0;
//       |    reset = 1'b1;
//       |  end
//       |  final begin
//       |    ${parameter.finalFunctionName}();
//       |  end
//       |  initial #(${parameter.resetFlipTick}) reset = 1'b0;
//       |  always #${parameter.clockFlipTick} clock = ~clock;
//       |endmodule
//       |""".stripMargin
//  )
//}
//