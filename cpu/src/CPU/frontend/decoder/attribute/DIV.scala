package cpu.frontend.decoder

object isDIV {
  def apply(CPUDecodePattern: CPUDecodePattern): isDIV = {
    val allMatched = Seq(
      "div", "divu", "rem", "remu"
    )
    allMatched.contains(CPUDecodePattern.instruction.name)
  }
}


trait DIVOpType extends Uop
object UopDIV extends DIVOpType
object UopDIVU extends DIVOpType
object UopREM extends DIVOpType
object UopREMU extends DIVOpType

object UopDIV {
    def apply(CPUDecodePattern: CPUDecodePattern): Uop = {
        CPUDecodePattern.instruction.name match {
            case "div"  => UopDIV
            case "divu" => UopDIVU
            case "rem"  => UopREM
            case "remu" => UopREMU
            case _      => UopDC
        }
    }
}

