package cpu.frontend.decoder

import chisel3._
import chisel3.util.BitPat
import chisel3.util.experimental.decode._
import org.chipsalliance.rvdecoderdb.Instruction
import org.chipsalliance.rvdecoderdb.Utils._

import cpu._
import utility._

object DecoderParam {
  implicit def rwP: upickle.default.ReadWriter[DecoderParam] = upickle.default.macroRW
}

case class DecoderParam(instructions: Seq[Instruction])

trait CPUDecodeFiled[D <: Data] extends DecodeField[CPUDecodePattern, D]

trait BoolField extends CPUDecodeFiled[Bool] with BoolDecodeField[CPUDecodePattern]

trait FuTypeUopField extends CPUDecodeFiled[UInt] {
  def chiselType: UInt = UInt(4.W)
}

trait FuOpTypeUopField extends CPUDecodeFiled[UInt] {
  def chiselType: UInt = UInt(7.W)
}
trait ImmUopField extends CPUDecodeFiled[UInt] {
  def chiselType: UInt = UInt(3.W)
}

object Decoder {
  implicit def uintToBitPat(x: UInt): BitPat = BitPat(x)

  object ReadRs1 extends BoolField {
    override def name: String = "ReadRs1"
    override def genTable(pattern: CPUDecodePattern): BitPat = if (readRs1(pattern.instruction)) y else n
  }

  object ReadRs2 extends BoolField {
    override def name: String = "ReadRs2"
    override def genTable(pattern: CPUDecodePattern): BitPat = if (readRs2(pattern.instruction)) y else n
  }

  object WriteRd extends BoolField {
    override def name: String = "WriteRd"
    override def genTable(pattern: CPUDecodePattern): BitPat = if (writeRd(pattern.instruction)) y else n
  }

  object Fu extends FuTypeUopField {
    // format: off
    override def name: String = "Fu"
    override def genTable(pattern: CPUDecodePattern): BitPat = pattern.instruction.name match {
        case i if Seq("lui","addi","andi","ori","xori","slti","sltiu","sll","add","sub","slt","sltu","and","or","xor","sra","srl","slli","srli","srai","addiw","slliw","sraiw","srliw","addw","subw","sllw","sraw","srlw").contains(i) => FuType.alu
        case i if Seq("beq","bne","blt","bge","bltu","bgeu").contains(i) => FuType.brh
        case i if Seq("csrrw","csrrs","csrrc","csrrwi","csrrsi","csrrci","ecall","ebreak","mret","sret","wfi").contains(i) => FuType.csr
        case i if Seq("fence","fencei","sfence.vma").contains(i) => FuType.fence
        case i if Seq("jal","jalr","auipc").contains(i) => FuType.jmp
        case i if Seq("lb","lh","lw","ld","lbu","lhu","lwu").contains(i) => FuType.ldu
        case i if Seq("sb","sh","sw","sd").contains(i) => FuType.stu
        case i if Seq("lr.w","sc.w","amoswap.w","amoadd.w","amoxor.w","amoand.w","amoor.w","amomin.w","amomax.w","amominu.w","amomaxu.w","lr.d","sc.d","amoswap.d","amoadd.d","amoxor.d","amoand.d","amoor.d","amomin.d","amomax.d","amominu.d","amomaxu.d").contains(i) => FuType.mou
        case i if Seq("mul","mulh","mulhsu","mulhu","mulw").contains(i) => FuType.mul
        case i if Seq("div","divu","rem","remu","divw","divuw","remw","remuw").contains(i) => FuType.div
        case _ => FuType.dontCare
    // format: on
    }
  }

  object FuOp extends FuOpTypeUopField {
    override def name: String = "FuOp"
    override def genTable(pattern: CPUDecodePattern): BitPat =
      pattern.instruction.name match {
        case i if Seq("lui", "addi", "add").contains(i) => ALUOpType.add
        case i if Seq("andi", "and").contains(i)        => ALUOpType.and
        case i if Seq("ori", "or").contains(i)          => ALUOpType.or
        case i if Seq("xori", "xor").contains(i)        => ALUOpType.xor
        case i if Seq("slti", "slt").contains(i)        => ALUOpType.slt
        case i if Seq("sltiu", "sltu").contains(i)      => ALUOpType.sltu
        case i if Seq("sll", "slli").contains(i)        => ALUOpType.sll
        case i if Seq("sub", "subw").contains(i)        => ALUOpType.sub
        case i if Seq("sra", "srai").contains(i)        => ALUOpType.sra
        case i if Seq("srl", "srli").contains(i)        => ALUOpType.srl
        case i if Seq("addiw", "addw").contains(i)      => ALUOpType.addw
        case i if Seq("slliw", "sllw").contains(i)      => ALUOpType.sllw
        case i if Seq("sraiw", "sraw").contains(i)      => ALUOpType.sraw
        case i if Seq("srliw", "srlw").contains(i)      => ALUOpType.srlw

        case i if Seq("beq").contains(i)  => BRUOpType.beq
        case i if Seq("bne").contains(i)  => BRUOpType.bne
        case i if Seq("blt").contains(i)  => BRUOpType.blt
        case i if Seq("bge").contains(i)  => BRUOpType.bge
        case i if Seq("bltu").contains(i) => BRUOpType.bltu
        case i if Seq("bgeu").contains(i) => BRUOpType.bgeu

        case i if Seq("csrrw").contains(i)                                  => CSROpType.csrrw
        case i if Seq("csrrs").contains(i)                                  => CSROpType.csrrs
        case i if Seq("csrrc").contains(i)                                  => CSROpType.csrrc
        case i if Seq("csrrwi").contains(i)                                 => CSROpType.csrrwi
        case i if Seq("csrrsi").contains(i)                                 => CSROpType.csrrsi
        case i if Seq("csrrci").contains(i)                                 => CSROpType.csrrci
        case i if Seq("ecall", "ebreak", "mret", "sret", "wfi").contains(i) => CSROpType.jmp

        case i if Seq("fence").contains(i)      => FenceOpType.fence
        case i if Seq("fencei").contains(i)     => FenceOpType.fencei
        case i if Seq("sfence.vma").contains(i) => FenceOpType.sfence

        case i if Seq("jal").contains(i)   => JumpOpType.jal
        case i if Seq("jalr").contains(i)  => JumpOpType.jalr
        case i if Seq("auipc").contains(i) => JumpOpType.auipc

        case i if Seq("lb").contains(i)  => LSUOpType.lb
        case i if Seq("lh").contains(i)  => LSUOpType.lh
        case i if Seq("lw").contains(i)  => LSUOpType.lw
        case i if Seq("ld").contains(i)  => LSUOpType.ld
        case i if Seq("lbu").contains(i) => LSUOpType.lbu
        case i if Seq("lhu").contains(i) => LSUOpType.lhu
        case i if Seq("lwu").contains(i) => LSUOpType.lwu

        case i if Seq("sb").contains(i) => LSUOpType.sb
        case i if Seq("sh").contains(i) => LSUOpType.sh
        case i if Seq("sw").contains(i) => LSUOpType.sw
        case i if Seq("sd").contains(i) => LSUOpType.sd

        case i if Seq("lr.w").contains(i)      => LSUOpType.lr_w
        case i if Seq("sc.w").contains(i)      => LSUOpType.sc_w
        case i if Seq("amoswap.w").contains(i) => LSUOpType.amoswap_w
        case i if Seq("amoadd.w").contains(i)  => LSUOpType.amoadd_w
        case i if Seq("amoxor.w").contains(i)  => LSUOpType.amoxor_w
        case i if Seq("amoand.w").contains(i)  => LSUOpType.amoand_w
        case i if Seq("amoor.w").contains(i)   => LSUOpType.amoor_w
        case i if Seq("amomin.w").contains(i)  => LSUOpType.amomin_w
        case i if Seq("amomax.w").contains(i)  => LSUOpType.amomax_w
        case i if Seq("amominu.w").contains(i) => LSUOpType.amominu_w
        case i if Seq("amomaxu.w").contains(i) => LSUOpType.amomaxu_w
        case i if Seq("lr.d").contains(i)      => LSUOpType.lr_d
        case i if Seq("sc.d").contains(i)      => LSUOpType.sc_d
        case i if Seq("amoswap.d").contains(i) => LSUOpType.amoswap_d
        case i if Seq("amoadd.d").contains(i)  => LSUOpType.amoadd_d
        case i if Seq("amoxor.d").contains(i)  => LSUOpType.amoxor_d
        case i if Seq("amoand.d").contains(i)  => LSUOpType.amoand_d
        case i if Seq("amoor.d").contains(i)   => LSUOpType.amoor_d
        case i if Seq("amomin.d").contains(i)  => LSUOpType.amomin_d
        case i if Seq("amomax.d").contains(i)  => LSUOpType.amomax_d
        case i if Seq("amominu.d").contains(i) => LSUOpType.amominu_d
        case i if Seq("amomaxu.d").contains(i) => LSUOpType.amomaxu_d

        case i if Seq("mul").contains(i)    => MDUOpType.mul
        case i if Seq("mulh").contains(i)   => MDUOpType.mulh
        case i if Seq("mulhsu").contains(i) => MDUOpType.mulhsu
        case i if Seq("mulhu").contains(i)  => MDUOpType.mulhu
        case i if Seq("mulw").contains(i)   => MDUOpType.mulw

        case i if Seq("div").contains(i)   => MDUOpType.div
        case i if Seq("divu").contains(i)  => MDUOpType.divu
        case i if Seq("rem").contains(i)   => MDUOpType.rem
        case i if Seq("remu").contains(i)  => MDUOpType.remu
        case i if Seq("divw").contains(i)  => MDUOpType.divw
        case i if Seq("divuw").contains(i) => MDUOpType.divuw
        case i if Seq("remw").contains(i)  => MDUOpType.remw
        case i if Seq("remuw").contains(i) => MDUOpType.remuw

        case _ => FuOpType.dontCare
      }
  }

  object ImmType extends ImmUopField {
    override def name: String = "ImmType"
    override def genTable(pattern: CPUDecodePattern): BitPat = {
      val instruction = pattern.instruction
      if (isI(instruction)) InstrType.I
      else if (isS(instruction)) InstrType.S
      else if (isB(instruction)) InstrType.B
      else if (isU(instruction)) InstrType.U
      else if (isJ(instruction)) InstrType.J
      else InstrType.dontCare
    }
  }

  def allFields(param: DecoderParam): Seq[CPUDecodeFiled[_ >: Bool <: UInt]] =
    Seq(ReadRs1, ReadRs2, WriteRd, Fu, FuOp, ImmType)
  def allDecodePattern(param: DecoderParam): Seq[CPUDecodePattern] =
    param.instructions.map(CPUDecodePattern(_, param)).toSeq.sortBy(_.instruction.name)
  def decodeTable(param: DecoderParam): DecodeTable[CPUDecodePattern] =
    new DecodeTable[CPUDecodePattern](allDecodePattern(param), allFields(param))
  def decode(param: DecoderParam): UInt => DecodeBundle = decodeTable(param).decode
}
