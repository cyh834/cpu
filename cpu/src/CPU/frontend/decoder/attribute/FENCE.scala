package cpu.frontend.decoder

object isFENCE {
  def apply(CPUDecodePattern: CPUDecodePattern): isFENCE = {
    val allMatched = Seq(
      "fence",
      "fence.i",
      "sfence.vma"
    )
    allMatched.contains(CPUDecodePattern.instruction.name)
  }
}

trait FenceUOpType extends Uop
object UopFence extends FenceUOpType
object UopFencei extends FenceUOpType
object UopSfence extends FenceUOpType

object UopFENCE {
  def apply(CPUDecodePattern: CPUDecodePattern): Uop = {
    CPUDecodePattern.instruction.name match {
      case "fence"      => UopFence
      case "fence.i"    => UopFencei
      case "sfence.vma" => UopSfence
    }
  }
}
