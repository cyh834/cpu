package cpu.frontend.decoder

object isSTU {
  def apply(CPUDecodePattern: CPUDecodePattern): isSTU = {
    val allMatched = Seq(
      "sw",
      "sh",
      "sb",
      "sd"
    )
    allMatched.contains(CPUDecodePattern.instruction.name)
  }
}

object UopSTU extends STUOpType
object UopSW extends STUOpType
object UopSH extends STUOpType
object UopSB extends STUOpType
object UopSD extends STUOpType

object UopSTU {
  def apply(CPUDecodePattern: CPUDecodePattern): Uop = {
    CPUDecodePattern.instruction.name match {
      case "sw" => UopSW
      case "sh" => UopSH
      case "sb" => UopSB
      case "sd" => UopSD
      case _    => UopDC
    }
  }
}
