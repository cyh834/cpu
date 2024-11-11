package cpu.frontend.decoder

object isFence {
  def apply(cpuDecodePattern: CPUDecodePattern): isFence = {
    val allMatched = Seq(
      "fence",
      "fence.i",
      "sfence.vma"
    )
    allMatched.contains(cpuDecodePattern.instruction.name)
  }
}

trait FenceUOPType extends Uop
object Uopfence extends FenceUOPType
object Uopfencei extends FenceUOPType
object Uopsfencevma extends FenceUOPType

object FenceUOP {
  def apply(cpuDecodePattern: CPUDecodePattern): Uop = {
    cpuDecodePattern.instruction.name match {
      case "fence"      => Uopfence
      case "fence.i"    => Uopfencei
      case "sfence.vma" => Uopsfencevma
    }
  }
}