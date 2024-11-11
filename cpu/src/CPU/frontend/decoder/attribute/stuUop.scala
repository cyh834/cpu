package cpu.frontend.decoder

object isStu {
  def apply(cpuDecodePattern: CPUDecodePattern): isStu = {
    val allMatched = Seq(
      "sw",
      "sh",
      "sb",
      "sd"
    )
    allMatched.contains(cpuDecodePattern.instruction.name)
  }
}

trait StuUOPType extends Uop
object Uopsw extends StuUOPType
object Uopsh extends StuUOPType
object Uopsb extends StuUOPType
object Uopsd extends StuUOPType

object StuUOP {
  def apply(cpuDecodePattern: CPUDecodePattern): Uop = {
    cpuDecodePattern.instruction.name match {
      case "sw" => Uopsw
      case "sh" => Uopsh
      case "sb" => Uopsb
      case "sd" => Uopsd
      case _    => UopDC
    }
  }
}