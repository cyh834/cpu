package cpu.frontend.decoder

object isLDU {
  def apply(CPUDecodePattern: CPUDecodePattern): isLDU = {
    val allMatched = Seq(
      "lw", "lh", "lb", "lbu", "lh", "lhu", "ld", "lwu"
    )
    allMatched.contains(CPUDecodePattern.instruction.name)
  }
}

trait LDU extends Uop
object UopLD extends LDU
object UopLW extends LDU
object UopLH extends LDU
object UopLHU extends LDU
object UopLB extends LDU
object UopLBU extends LDU
object UopLWU extends LDU
object UopLDU extends LDU

object UopLD {
    def apply(CPUDecodePattern: CPUDecodePattern): Uop = {
        CPUDecodePattern.instruction.name match {
            case "ld"  => UopLD
            case "lw"  => UopLW
            case "lh"  => UopLH
            case "lhu" => UopLHU
            case "lb"  => UopLB
            case "lbu" => UopLBU
            case "lwu" => UopLWU
            case "ldu" => UopLDU
            case _     => UopDC
        }
    }
}