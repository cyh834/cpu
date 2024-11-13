//package cpu.cpuemu.vip
//
//import chisel3._
//import chisel3.util.circt.dpi.{RawClockedVoidFunctionCall, RawUnclockedNonVoidFunctionCall}
//import chisel3.util.{isPow2, log2Ceil}
//import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
//import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
//import amba.axi4._
//
//class AXI4VIPInterface(parameter: AXI4BundleParameter) extends AXI4RWIrrevocable(parameter)
//
//@instantiable
//class AXI4VIP(val parameter: AXI4BundleParameter) extends FixedIORawModule(new AXI4VIPInterface(parameter)) {
//  dontTouch(io)
//}
//
