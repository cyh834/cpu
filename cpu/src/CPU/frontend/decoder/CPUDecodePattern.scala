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

  def isALU:   isALU = isALU(this)
  def isBRU:   isBRU = isBRU(this)
  def isCSR:   isCSR = isCSR(this)
  def isDIV:   isDIV = isDIV(this)
  def isJMP:   isJMP = isJMP(this)
  def isLDU:   isLDU = isLDU(this)
  def isMOU:   isMOU = isMOU(this)
  def isMUL:   isMUL = isMUL(this)
  def isSTU:   isSTU = isSTU(this)
  def isFENCE: isFENCE = isFENCE(this)

  def FuOpType: FuOpType = FuOptype(this)

  def ReadSrc1: ReadSrc1 = ReadSrc1(this)
  def ReadSrc2: ReadSrc2 = ReadSrc2(this)
  def WriteRd:  WriteRd = WriteRd(this)

  def isR = isR(this)
  def isI = isI(this)
  def isS = isS(this)
  def isB = isB(this)
  def isU = isU(this)
  def isJ = isJ(this)
}
