package cpu.frontend.decoder

object isJmp {
  def apply(cpuDecodePattern: CPUDecodePattern): Boolean = {
    val allMatched = Seq(
      "jal",
      "jalr",
      "auipc"
    )
    allMatched.contains(cpuDecodePattern.instruction.name)
  }
}

trait JmpUOPType extends Uop
object Uopjal extends JmpUOPType
object Uopjalr extends JmpUOPType
object Uopauipc extends JmpUOPType

object JmpUOP {
  def apply(cpuDecodePattern: CPUDecodePattern): Uop = {
    cpuDecodePattern.instruction.name match {
      case "jal"   => Uopjal
      case "jalr"  => Uopjalr
      case "auipc" => Uopauipc
      case _       => UopDC
    }
  }
}

case class JmpUOP(value: JmpUOPType) extends UopDecodeAttribute[JmpUOPType]