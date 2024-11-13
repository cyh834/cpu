package cpu.frontend.decoder

object isMou {
  def apply(cpuDecodePattern: CPUDecodePattern): Boolean = {
    val allMatched = Seq(
      "lr.w",
      "sc.w",
      "amoswap.w",
      "amoadd.w",
      "amoxor.w",
      "amoand.w",
      "amoor.w",
      "amomin.w",
      "amomax.w",
      "amominu.w",
      "amomaxu.w",
      
      "lr.d",
      "sc.d",
      "amoswap.d",
      "amoadd.d",
      "amoxor.d",
      "amoand.d",
      "amoor.d",
      "amomin.d",
      "amomax.d",
      "amominu.d",
      "amomaxu.d"
    )
    allMatched.contains(cpuDecodePattern.instruction.name)
  }
}

trait MouUOPType extends Uop
object Uoplrw extends MouUOPType
object Uopscw extends MouUOPType
object Uopamoswapw extends MouUOPType
object Uopamoaddw extends MouUOPType
object Uopamoxorw extends MouUOPType
object Uopamoandw extends MouUOPType
object Uopamoorw extends MouUOPType
object Uopamominw extends MouUOPType
object Uopamomaxw extends MouUOPType
object Uopamominuw extends MouUOPType
object Uopamomaxuw extends MouUOPType

object Uoplrd extends MouUOPType
object Uopscd extends MouUOPType
object Uopamoswapd extends MouUOPType
object Uopamoaddd extends MouUOPType
object Uopamoxord extends MouUOPType
object Uopamoandd extends MouUOPType
object Uopamoord extends MouUOPType
object Uopamomind extends MouUOPType
object Uopamomaxd extends MouUOPType
object Uopamominud extends MouUOPType
object Uopamomaxud extends MouUOPType

object MouUOP {
  def apply(cpuDecodePattern: CPUDecodePattern): Uop = {
    cpuDecodePattern.instruction.name match {
      case "lr.w"      => Uoplrw
      case "sc.w"      => Uopscw
      case "amoswap.w" => Uopamoswapw
      case "amoadd.w"  => Uopamoaddw
      case "amoxor.w"  => Uopamoxorw
      case "amoand.w"  => Uopamoandw
      case "amoor.w"   => Uopamoorw
      case "amomin.w"  => Uopamominw
      case "amomax.w"  => Uopamomaxw
      case "amominu.w" => Uopamominuw
      case "amomaxu.w" => Uopamomaxuw
      
      case "lr.d"      => Uoplrd
      case "sc.d"      => Uopscd
      case "amoswap.d" => Uopamoswapd
      case "amoadd.d"  => Uopamoaddd
      case "amoxor.d"  => Uopamoxord
      case "amoand.d"  => Uopamoandd
      case "amoor.d"   => Uopamoord
      case "amomin.d"  => Uopamomind
      case "amomax.d"  => Uopamomaxd
      case "amominu.d" => Uopamominud
      case "amomaxu.d" => Uopamomaxud
      case _           => UopDC
    }
  }
}

case class MouUOP(value: MouUOPType) extends UopDecodeAttribute[MouUOPType]