package cpu.frontend.decoder

object isAlu {
  def apply(cpuDecodePattern: CPUDecodePattern): Boolean = {
    val allMatched = Seq(
      "lui",
      "addi",
      "andi",
      "ori",
      "xori",
      "slti",
      "sltiu",
      "sll",
      "add",
      "sub",
      "slt",
      "sltu",
      "and",
      "or",
      "xor",
      "sra",
      "srl",

      "slli",
      "srli",
      "srai",

      "addiw",
      "slliw",
      "sraiw",
      "srliw",

      "addw",
      "subw",
      "sllw",
      "sraw",
      "srlw"
    )
    allMatched.contains(cpuDecodePattern.instruction.name)
  }
}

trait AluUOPType extends Uop
object Uoplui extends AluUOPType
object Uopaddi extends AluUOPType
object Uopandi extends AluUOPType
object Uopori extends AluUOPType
object Uopxori extends AluUOPType
object Uopslti extends AluUOPType
object Uopsltiu extends AluUOPType
object Uopsll extends AluUOPType
object Uopadd extends AluUOPType
object Uopsub extends AluUOPType
object Uopslt extends AluUOPType
object Uopsltu extends AluUOPType
object Uopand extends AluUOPType
object Uopor extends AluUOPType
object Uopxor extends AluUOPType
object Uopsra extends AluUOPType
object Uopsrl extends AluUOPType
object Uopslli extends AluUOPType
object Uopsrli extends AluUOPType
object Uopsrai extends AluUOPType
object Uopaddiw extends AluUOPType
object Uopslliw extends AluUOPType
object Uopsraiw extends AluUOPType
object Uopsrliw extends AluUOPType
object Uopaddw extends AluUOPType
object Uopsubw extends AluUOPType
object Uopsllw extends AluUOPType
object Uopsraw extends AluUOPType
object Uopsrlw extends AluUOPType

object AluUOP {
  def apply(cpuDecodePattern: CPUDecodePattern): Uop = {
    cpuDecodePattern.instruction.name match {
      case "lui"   => Uoplui
      case "addi"  => Uopaddi
      case "andi"  => Uopandi
      case "ori"   => Uopori
      case "xori"  => Uopxori
      case "slti"  => Uopslti
      case "sltiu" => Uopsltiu
      case "sll"   => Uopsll
      case "add"   => Uopadd
      case "sub"   => Uopsub
      case "slt"   => Uopslt
      case "sltu"  => Uopsltu
      case "and"   => Uopand
      case "or"    => Uopor
      case "xor"   => Uopxor
      case "sra"   => Uopsra
      case "srl"   => Uopsrl
      case "slli"  => Uopslli
      case "srli"  => Uopsrli
      case "srai"  => Uopsrai
      case "addiw" => Uopaddiw
      case "slliw" => Uopslliw
      case "sraiw" => Uopsraiw
      case "srliw" => Uopsrliw
      case "addw"  => Uopaddw
      case "subw"  => Uopsubw
      case "sllw"  => Uopsllw
      case "sraw"  => Uopsraw
      case "srlw"  => Uopsrlw
      case _       => UopDC
    }
  }
}

case class AluUOP(value: AluUOPType) extends UopDecodeAttribute[AluUOPType]