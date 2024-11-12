package cpu.frontend.decoder

object isBru {
  def apply(cpuDecodePattern: CPUDecodePattern): isBru = {
    val allMatched = Seq(
      "beq",
      "bne",
      "bge",
      "bgeu",
      "blt",
      "bltu"
    )
    allMatched.contains(cpuDecodePattern.instruction.name)
  }
}

trait BruUOPType extends Uop
object Uopbeq extends BruUOPType
object Uopbne extends BruUOPType
object Uopbge extends BruUOPType
object Uopbgeu extends BruUOPType
object Uopblt extends BruUOPType
object Uopbltu extends BruUOPType


object BruUOP {
  def apply(cpuDecodePattern: CPUDecodePattern): Uop = {
    cpuDecodePattern.instruction.name match {
      case "beq"  => Uopbeq
      case "bne"  => Uopbne
      case "bge"  => Uopbge
      case "bgeu" => Uopbgeu
      case "blt"  => Uopblt
      case "bltu" => Uopbltu
    }
  }
}