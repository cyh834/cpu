package cpu.frontend.decoder

object isCSR {
  def apply(CPUDecodePattern: CPUDecodePattern): isCSR = {
    val allMatched = Seq(
      "csrrw", "csrrs", "csrrc", "csrrwi", "csrrsi", "csrrci"
    )
    allMatched.contains(CPUDecodePattern.instruction.name)
  }
}

trait CSROpType extends Uop
object UopCSRRC extends CSROpType
object UopCSRRCI extends CSROpType
object UopCSRRS extends CSROpType
object UopCSRRSI extends CSROpType
object UopCSRRW extends CSROpType
object UopCSRRWI extends CSROpType

object CSRType {
  def apply(CPUDecodePattern: CPUDecodePattern): CSROpType = {
    CPUDecodePattern.instruction.name match {
      case "csrrw" => UopCSRRW
      case "csrrwi" => UopCSRRWI
      case "csrrs" => UopCSRRS
      case "csrrsi" => UopCSRRSI
      case "csrrc" => UopCSRRC
      case "csrrci" => UopCSRRCI
    }
  }
}