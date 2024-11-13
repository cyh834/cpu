package cpu.frontend.decoder

object isCsr {
  def apply(cpuDecodePattern: CPUDecodePattern): Boolean = {
    val allMatched = Seq(
      "csrrw",
      "csrrs",
      "csrrc",
      "csrrwi",
      "csrrsi",
      "csrrci",

      "ecall",
      "ebreak",
      "mret",
      "sret",
      "wfi"
    )
    allMatched.contains(cpuDecodePattern.instruction.name)
  }
}

trait CsrUOPType extends Uop
object Uopcsrrw extends CsrUOPType
object Uopcsrrs extends CsrUOPType
object Uopcsrrc extends CsrUOPType
object Uopcsrrwi extends CsrUOPType
object Uopcsrrsi extends CsrUOPType
object Uopcsrrci extends CsrUOPType
object Uopecall extends CsrUOPType
object Uopebreak extends CsrUOPType
object Uopmret extends CsrUOPType
object Uopsret extends CsrUOPType
object Uopwfi extends CsrUOPType

object CsrUOP{
  def apply(cpuDecodePattern: CPUDecodePattern): Uop = {
    cpuDecodePattern.instruction.name match {
      case "csrrw"  => Uopcsrrw
      case "csrrwi" => Uopcsrrwi
      case "csrrs"  => Uopcsrrs
      case "csrrsi" => Uopcsrrsi
      case "csrrc"  => Uopcsrrc
      case "csrrci" => Uopcsrrci
      case "ecall"  => Uopecall
      case "ebreak" => Uopebreak
      case "mret"   => Uopmret
      case "sret"   => Uopsret
      case "wfi"    => Uopwfi
    }
  }
}

case class CsrUOP(value: CsrUOPType) extends UopDecodeAttribute[CsrUOPType]