package cpu.frontend.decoder

object isJMP {
  def apply(CPUDecodePattern: CPUDecodePattern): isJMP = {
    val allMatched = Seq(
      "jal",
      "jalr",
      "auipc"
    )
    allMatched.contains(CPUDecodePattern.instruction.name)
  }
}

trait JMPOpType extends Uop
object UopJAL extends JMPOpType
object UopJALR extends JMPOpType
object UopAUIPC extends JMPOpType

object UopJAL {
  def apply(CPUDecodePattern: CPUDecodePattern): Uop = {
    CPUDecodePattern.instruction.name match {
      case "jal"   => UopJAL
      case "jalr"  => UopJALR
      case "auipc" => UopAUIPC
      case _       => UopDC
    }
  }
}
