package cpu.frontend.decoder

import chisel3._
import chisel3.experimental.hierarchy.core.Definition
import chisel3.experimental.hierarchy.{instantiable, public, Instantiate}
import chisel3.properties.{AnyClassType, Class, ClassType, Property}
import chisel3.util.BitPat
import chisel3.util.experimental.decode.DecodePattern
import org.chipsalliance.rvdecoderdb.Instruction
import org.chipsalliance.rvdecoderdb.Utils._

trait Uop
object UopDC extends Uop

case class CPUDecodePattern(instruction: Instruction, param: DecoderParam) extends DecodePattern {
  override def bitPat: BitPat = BitPat("b" + instruction.encoding.toString)

  def isALU:   isAlu = isAlu(this)
  def isBRU:   isBru = isBru(this)
  def isCSR:   isCsr = isCsr(this)
  def isDIV:   isDiv = isDiv(this)
  def isJMP:   isJmp = isJmp(this)
  def isLDU:   isLdu = isLdu(this)
  def isMOU:   isMou = isMou(this)
  def isMUL:   isMul = isMul(this)
  def isSTU:   isStu = isStu(this)
  def isFENCE: isFence = isFence(this)

  def FUOpType: FUOpType = FUOpType(this)

  def ReadRs1: Boolean = readRs1(this)
  def ReadRs2: Boolean = readRs2(this)
  def WriteRd: Boolean = writeRd(this)

  def isR: Boolean = isR(this)
  def isI: Boolean = isI(this)
  def isS: Boolean = isS(this)
  def isB: Boolean = isB(this)
  def isU: Boolean = isU(this)
  def isJ: Boolean = isJ(this)
}
