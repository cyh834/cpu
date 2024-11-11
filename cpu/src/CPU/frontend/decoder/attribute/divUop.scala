package cpu.frontend.decoder

object isDiv {
  def apply(cpuDecodePattern: CPUDecodePattern): isDiv = {
    val allMatched = Seq(
      "div",
      "divu",
      "rem",
      "remu",

      "divw",
      "divuw",
      "remw",
      "remuw"
    )
    allMatched.contains(cpuDecodePattern.instruction.name)
  }
}

trait DivUOPType extends Uop
object Uopdiv extends DivUOPType
object Uopdivu extends DivUOPType
object Uoprem extends DivUOPType
object Uopremu extends DivUOPType
object Uopdivw extends DivUOPType
object Uopdivuw extends DivUOPType
object Uopremw extends DivUOPType
object Uopremuw extends DivUOPType

object DivUOP {
  def apply(cpuDecodePattern: CPUDecodePattern): Uop = {
    cpuDecodePattern.instruction.name match {
      case "div"   => Uopdiv
      case "divu"  => Uopdivu
      case "rem"   => Uoprem
      case "remu"  => Uopremu
      case "divw"  => Uopdivw
      case "divuw" => Uopdivuw
      case "remw"  => Uopremw
      case "remuw" => Uopremuw
      case _       => UopDC
    }
  }
}