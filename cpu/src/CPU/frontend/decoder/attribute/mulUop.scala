package cpu.frontend.decoder

object isMul {
  def apply(cpuDecodePattern: CPUDecodePattern): isMul = {
    val allMatched = Seq(
      "mul",
      "mulh",
      "mulhsu",
      "mulhu",
      "mulw"
    )
    allMatched.contains(cpuDecodePattern.instruction.name)
  }
}

trait MulUOPType extends Uop
object Uopmul extends MulUOPType
object Uopmulh extends MulUOPType
object Uopmulhsu extends MulUOPType
object Uopmulhu extends MulUOPType
object Uopmulw extends MulUOPType

object MulUOP {
  def apply(cpuDecodePattern: CPUDecodePattern): Uop = {
    cpuDecodePattern.instruction.name match {
      case "mul"    => Uopmul
      case "mulh"   => Uopmulh
      case "mulhsu" => Uopmulhsu
      case "mulhu"  => Uopmulhu
      case "mulw"   => Uopmulw
      case _        => UopDC
    }
  }
}
