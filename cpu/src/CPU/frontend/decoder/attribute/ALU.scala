package cpu.frontend.decoder

object isALU {
  def apply(CPUDecodePattern: CPUDecodePattern): isALU = {
    val allMatched = Seq(
      "sll", "slli", "srl", "srli", "sra", "srai", 
      "and", "andi", "or", "ori", "xor", "xori", 
      "sub", "subi", "slt", "slti", "sltu", "sltiu", 
      "addw", "addiw", "subw", "subiw", "sllw", "slliw"
    )
    allMatched.contains(CPUDecodePattern.instruction.name)
  }
}

trait ALUUOpType extends Uop
object UopSll  extends ALUUOpType
object UopSrl  extends ALUUOpType
object UopSra  extends ALUUOpType
object UopAnd  extends ALUUOpType
object UopOr   extends ALUUOpType
object UopXor  extends ALUUOpType
object UopSub  extends ALUUOpType
object UopSlt  extends ALUUOpType
object UopSltu extends ALUUOpType
object UopAddw extends ALUUOpType
object UopSubw extends ALUUOpType
object UopSllw extends ALUUOpType
object UopSrlw extends ALUUOpType
object UopSraw extends ALUUOpType
object UopAdd  extends ALUUOpType

object UopALU {
  def apply(CPUDecodePattern: CPUDecodePattern): Uop     = {
    CPUDecodePattern.instruction.name match {
      case "slL"  || "slli"          => UopSll
      case "srl"  || "srli"          => UopSrl
      case "sra"  || "srai"          => UopSra
      case "and"  || "andi"          => UopAnd
      case "or"   || "ori"           => UopOr
      case "xor"  || "xori"          => UopXor
      case "sub"  || "subi"          => UopSub
      case "slt"  || "slti"          => UopSlt
      case "sltu" || "sltiu"         => UopSltu
      case "addw" || "addiw"         => UopAddw
      case "subw" || "subiw"         => UopSubw
      case "sllw" || "slliw"         => UopSllw
      case "srlw" || "srliw"         => UopSrlw
      case "sraw" || "sraiw"         => UopSraw
      case "lui"  || "add" || "addi" => UopAdd
      case _ => UopDC
    }
  }
}