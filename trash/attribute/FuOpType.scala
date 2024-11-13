package cpu.frontend.decoder

object FUOpType {
  def apply(cpuDecodePattern: CPUDecodePattern): FUOpType = {
    val tpe: Option[FUOpType] = Seq(
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
      case (fn, tpe) if fn => FUOpType(tpe)
    }
    require(tpe.size <= 1)
    tpe.getOrElse(FUOpType(UopDC))
  }
}

case class FUOpType(value: Uop) extends UopDecodeAttribute[Uop]