package cpu.frontend.decoder

object isBRU {
  def apply(CPUDecodePattern: CPUDecodePattern): isBRU = {
    val allMatched = Seq(
      "beq", "bne", "blt", "bge", "bltu", "bgeu"
    )
    allMatched.contains(CPUDecodePattern.instruction.name)
  }
}

trait BRUOpType extends Uop
object UopBEQ extends BRUOpType 
object UopBNE extends BRUOpType 
object UopBLT extends BRUOpType 
object UopBGE extends BRUOpType 
object UopBLTU extends BRUOpType 
object UopBGEU extends BRUOpType 

object UopBRU{
  def apply(CPUDecodePattern: CPUDecodePattern): Uop = {
    CPUDecodePattern.instruction.name match {
      case "beq" => UopBEQ
      case "bne" => UopBNE
      case "blt" => UopBLT
      case "bge" => UopBGE
      case "bltu" => UopBLTU
      case "bgeu" => UopBGEU
    }
  }
}