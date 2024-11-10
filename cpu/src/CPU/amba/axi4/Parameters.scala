package amba.axi4

import chisel3._
import chisel3.util._

object AXI4BundleParameter {
  implicit def rw: upickle.default.ReadWriter[AXI4BundleParameter] =
    upickle.default.macroRW[AXI4BundleParameter]
}

case class AXI4BundleParameters(
  idWidth: Int,
  dataWidth: Int,
  addrWidth: Int,
  isRO: Boolean,
) {
  require (dataBits >= 8, s"AXI4 data bits must be >= 8 (got $dataBits)")
  require (addrBits >= 1, s"AXI4 addr bits must be >= 1 (got $addrBits)")
  require (idBits >= 1, s"AXI4 id bits must be >= 1 (got $idBits)")
  require (isPow2(dataBits), s"AXI4 data bits must be pow2 (got $dataBits)")

  val isRW = !isRO
  val isWO = false

  val awUserWidth: Int = 0
  val wUserWidth: Int = 0
  val bUserWidth: Int = 0
  val arUserWidth: Int = 0
  val rUserWidth: Int = 0
}

object irrevocable {
  def apply(parameter: AXI4BundleParameter): AXI4ChiselBundle = {
    if (parameter.isRO) new AXI4ROIrrevocable(parameter)
    else if (parameter.isRW) new AXI4RWIrrevocable(parameter)
  }
}