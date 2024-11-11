package cpu.frontend.decoder

object isLdu {
  def apply(cpuDecodePattern: CPUDecodePattern): isLdu = {
    val allMatched = Seq(
      "lw",
      "lh",
      "lhu",
      "lb",
      "lbu",

      "ld",
      "lwu"
    )
    allMatched.contains(cpuDecodePattern.instruction.name)
  }
}

trait LduUOPType extends Uop
object Uoplw extends LduUOPType
object Uoplh extends LduUOPType
object Uoplhu extends LduUOPType
object Uoplb extends LduUOPType
object Uoplbu extends LduUOPType
object Uopld extends LduUOPType
object Uoplwu extends LduUOPType

object LduUOP {
  def apply(cpuDecodePattern: CPUDecodePattern): Uop = {
    cpuDecodePattern.instruction.name match {
      case "lw"  => Uoplw
      case "lh"  => Uoplh
      case "lhu" => Uoplhu
      case "lb"  => Uoplb
      case "lbu" => Uoplbu
      case "ld"  => Uopld
      case "lwu" => Uoplwu
      case _     => UopDC
    }
  }
}