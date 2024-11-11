package cpu.frontend.decoder

import org.chipsalliance.rvdecoderdb.Utils._

object ReadSrc1 {
  def apply(CPUDecodePattern: CPUDecodePattern): ReadSrc0 = {
    readRs1(CPUDecodePattern.instruction)
  }
}

object ReadSrc2 {
  def apply(CPUDecodePattern: CPUDecodePattern): ReadSrc1 = {
    readRs2(CPUDecodePattern.instruction)
  }
}

object WriteRd {
  def apply(CPUDecodePattern: CPUDecodePattern): WriteRd = {
    writeRd(CPUDecodePattern.instruction)
  }
}
