package cpu.frontend.decoder

object FUOpType {
  def apply(cpuDecodePattern: CPUDecodePattern): FUOptype = {
    val tpe: Option[FUOptype] = Seq(
        isAlu(cpuDecodePattern) ->   AluUOP    (cpuDecodePattern)
        isBru(cpuDecodePattern) ->   BruUOP    (cpuDecodePattern)
        isLdu(cpuDecodePattern) ->   LsuUOP    (cpuDecodePattern)
        isStu(cpuDecodePattern) ->   LsuUOP    (cpuDecodePattern)
        isMou(cpuDecodePattern) ->   LsuUOP    (cpuDecodePattern)
        isMul(cpuDecodePattern) ->   MulUOP    (cpuDecodePattern)
        isDiv(cpuDecodePattern) ->   DivUOP    (cpuDecodePattern)
        isJmp(cpuDecodePattern) ->   JumpUOP   (cpuDecodePattern)
        isFence(cpuDecodePattern)->  FenceUOP  (cpuDecodePattern)
        isCsr(cpuDecodePattern) ->   CsrUOP    (cpuDecodePattern)
    ).collectFirst {
      case (fn, tpe) if fn => FUOptype(tpe)
    }
    require(tpe.size <= 1)
    tpe.getOrElse(FUOptype(UopDC))
  }
}

case class FUOptype(value: Uop) extends UopDecodeAttribute[Uop] {
  override val description: String = "uop for mask unit."
}