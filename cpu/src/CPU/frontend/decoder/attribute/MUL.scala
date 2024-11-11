package cpu.frontend.decoder

object isMUL {
  def apply(CPUDecodePattern: CPUDecodePattern): isMUL = {
    val allMatched = Seq(
      "mul",
      "mulh",
      "mulhsu",
      "mulhu"
    )
    allMatched.contains(CPUDecodePattern.instruction.name)
  }
}

trait MULOpType extends Uop
object UopMUL extends MULOpType
object UopMULH extends MULOpType
object UopMULHSU extends MULOpType
object UopMULHU extends MULOpType

object UopMUL {
  def apply(CPUDecodePattern: CPUDecodePattern): Uop = {
    CPUDecodePattern.instruction.name match {
      case "mul"    => UopMUL
      case "mulh"   => UopMULH
      case "mulhsu" => UopMULHSU
      case "mulhu"  => UopMULHU
      case _        => UopDC
    }
  }
}
