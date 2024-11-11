package cpu.frontend.decoder

object FuOpType {
  def apply(CPUDecodePattern: CPUDecodePattern): FuOptype = {
    val tpe: Option[FuOptype] = Seq(
      (isALU(CPUDecodePattern) -> ALUOpType(CPUDecodePattern))
        .isBRU((CPUDecodePattern) -> BRUOpType(CPUDecodePattern))
        .isLDU((CPUDecodePattern) -> LSUOpType(CPUDecodePattern))
        .isSTU((CPUDecodePattern) -> LSUOpType(CPUDecodePattern))
        .isMOU((CPUDecodePattern) -> LSUOpType(CPUDecodePattern))
        .isMUL((CPUDecodePattern) -> MulOpType(CPUDecodePattern))
        .isDIV((CPUDecodePattern) -> DivOpType(CPUDecodePattern))
        .isJMP((CPUDecodePattern) -> JumpOpType(CPUDecodePattern))
        .isFENCE((CPUDecodePattern) -> FenceOpType(CPUDecodePattern))
        .isCSR((CPUDecodePattern) -> CSROpType(CPUDecodePattern))
    ).collectFirst {
      case (fn, tpe) if fn => FuOptype(tpe)
    }
    require(tpe.size <= 1)
    tpe.getOrElse(FuOptype(UopDC))
  }
}

case class FuOptype(value: Uop) extends UopDecodeAttribute[Uop] {
  override val description: String = "uop for mask unit."
}
