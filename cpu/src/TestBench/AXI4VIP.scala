package cpu.cpuemu.vip

import chisel3._
import chisel3.util.circt.dpi.{RawClockedVoidFunctionCall, RawUnclockedNonVoidFunctionCall}
import chisel3.util.{isPow2, log2Ceil}
import cpu.amba.axi4

class AXI4VIPInterface(parameter: AXI4BundleParameter) extends AXI4RWIrrevocable

@instantiable
class AXI4VIP(val parameter: AXI4BundleParameter) extends FixedIORawModule(new AXI4VIPInterface(parameter)) {
  dontTouch(io)

}
