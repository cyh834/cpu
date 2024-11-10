package cpu.frontend.decoder

object isMOU {
  def apply(CPUDecodePattern: CPUDecodePattern): isMOU = {
    val allMatched = Seq(
      "mv", "add", "sub", "neg", "not", "sext.w", "seqz", "snez", "sltz", "sgtz", "fence"
    )
    allMatched.contains(CPUDecodePattern.instruction.name)
  }
}

trait MOUOpType extends Uop
object UopMV extends MOUOpType
object UopADD extends MOUOpType
object UopSUB extends MOUOpType
object UopNEG extends MOUOpType
object UopNOT extends MOUOpType
object UopSEXTW extends MOUOpType
object UopSEQZ extends MOUOpType
object UopSNEZ extends MOUOpType

object UopMOU {
    def apply(CPUDecodePattern: CPUDecodePattern): Uop = {
        CPUDecodePattern.instruction.name match {
            case "mv"     => UopMV
            case "add"    => UopADD
            case "sub"    => UopSUB
            case "neg"    => UopNEG
            case "not"    => UopNOT
            case "sext.w" => UopSEXTW
            case "seqz"   => UopSEQZ
            case "snez"   => UopSNEZ
            case _        => UopDC
        }
    }
}