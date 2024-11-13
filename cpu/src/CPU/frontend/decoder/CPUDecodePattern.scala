package cpu.frontend.decoder

import chisel3._
import chisel3.experimental.hierarchy.core.Definition
import chisel3.experimental.hierarchy.{instantiable, public, Instantiate}
import chisel3.properties.{AnyClassType, Class, ClassType, Property}
import chisel3.util.BitPat
import chisel3.util.experimental.decode.DecodePattern
import org.chipsalliance.rvdecoderdb.Instruction
import org.chipsalliance.rvdecoderdb.Utils._

case class CPUDecodePattern(instruction: Instruction, param: DecoderParam) extends DecodePattern {
  override def bitPat: BitPat = BitPat("b" + instruction.encoding.toString)

  // def isR: Boolean = isR(instruction)
  // def isI: Boolean = isI(instruction)
  // def isS: Boolean = isS(instruction)
  // def isB: Boolean = isB(instruction)
  // def isU: Boolean = isU(instruction)
  // def isJ: Boolean = isJ(instruction)
}
