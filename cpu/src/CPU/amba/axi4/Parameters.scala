package amba.axi4

import chisel3._
import chisel3.util._

object AXI4BundleParameter {
  implicit def rwP: upickle.default.ReadWriter[AXI4BundleParameter] =
    upickle.default.macroRW[AXI4BundleParameter]
}

case class AXI4BundleParameter(
  idWidth:   Int,
  dataWidth: Int,
  addrWidth: Int,
  isRO:      Boolean) {

  val isRW = !isRO
  val isWO = false

  val awUserWidth: Int = 0
  val wUserWidth:  Int = 0
  val bUserWidth:  Int = 0
  val arUserWidth: Int = 0
  val rUserWidth:  Int = 0
}

object AXI4 {
  def apply(parameter: AXI4BundleParameter) = {
    if (parameter.isRW) new AXI4RWIrrevocable(parameter)
    else new AXI4ROIrrevocable(parameter)
  }
}

object AXI4Parameters{
   object burst {
     val FIXED = 0.U(2.W)
     val INCR = 1.U(2.W)
     val WRAP = 2.U(2.W)
   }
}