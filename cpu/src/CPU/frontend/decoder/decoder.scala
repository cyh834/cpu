package cpu.frontend.decoder

import chisel3._
import chisel3.util.BitPat
import chisel3.util.experimental.decode._
import org.chipsalliance.rvdecoderdb.Instruction

import cpu._
import utility._

object DecoderParam {
  implicit def rwP: upickle.default.ReadWriter[DecoderParam] = upickle.default.macroRW
}

case class DecoderParam(allInstructions: Seq[Instruction])

trait CPUDecodeFiled[D <: Data] extends DecodeField[CPUDecodePattern, D] with FieldName

trait BoolField extends CPUDecodeFiled[Bool] with BoolDecodeField[CPUDecodePattern]

trait FuTypeUopField extends CPUDecodeFiled[UInt] with FieldName {
  def chiselType: UInt = UInt(4.W)
}

trait FuOpTypeUopField extends CPUDecodeFiled[UInt] with FieldName {
  def chiselType: UInt = UInt(4.W)
}
trait ImmUopField extends CPUDecodeFiled[UInt] with FieldName {
  def chiselType: UInt = UInt(32.W)
}


object Decoder{
    implicit def uintToBitPat(x: UInt): BitPat = BitPat(x)

    object ReadSrc1 extends BoolField {
        override def genTable(pattern: T1DecodePattern): BitPat = if (pattern.ReadSrc1) y else n
    }

    object ReadSrc2 extends BoolField {
        override def genTable(pattern: T1DecodePattern): BitPat = if (pattern.ReadSrc2) y else n
    }

    object WriteRd extends BoolField {
        override def genTable(pattern: T1DecodePattern): BitPat = if (pattern.WriteRd) y else n
    }

    object Fu extends FuTypeUopField {
        override def genTable(pattern: T1DecodePattern): BitPat = 
          if(pattern.isALU)         FuType.alu
          else if(pattern.isBRU)    FuType.bru
          else if(pattern.isCSR)    FuType.csr
          else if(pattern.isDIV)    FuType.div
          else if(pattern.isFENCE)  FuType.fence
          else if(pattern.isJMP)    FuType.jmp
          else if(pattern.isLDU)    FuType.ldu
          else if(pattern.isMOU)    FuType.mou
          else if(pattern.isMUL)    FuType.mul
          else if(pattern.isSTU)    FuType.stu
          else BitPat.dontCare(4)
    }

//TODO: Too long
    object FuOp extends FuOpTypeUopField {
      override def genTable(pattern: CPUDecodePattern): BitPat = pattern.FuOpType match {
        case aluCase: ALUOpType => 
          aluCase match {
            case _: UopSll.type  => ALUOpType.sll
            case _: UopSrl.type  => ALUOpType.srl
            case _: UopSra.type  => ALUOpType.sra
            case _: UopAdd.type  => ALUOpType.add
            case _: UopAnd.type  => ALUOpType.and
            case _: UopOr.type   => ALUOpType.or
            case _: UopXor.type  => ALUOpType.xor
            case _: UopSub.type  => ALUOpType.sub
            case _: UopSltu.type => ALUOpType.sltu
            case _: UopSlt.type  => ALUOpType.slt
            case _: UopAddw.type => ALUOpType.addw
            case _: UopSubw.type => ALUOpType.subw
            case _: UopSllw.type => ALUOpType.sllw
            case _: UopSrlw.type => ALUOpType.srlw
            case _: UopSraw.type => ALUOpType.sraw
            case _ => BitPat.dontCare(7)
          }  
      case bruCase: BRUOpType => 
        bruCase match {
          case _: UopBeq.type  => BRUOpType.beq
          case _: UopBne.type  => BRUOpType.bne
          case _: UopBlt.type  => BRUOpType.blt
          case _: UopBge.type  => BRUOpType.bge
          case _: UopBltu.type => BRUOpType.bltu
          case _: UopBgeu.type => BRUOpType.bgeu
          case _ => BitPat.dontCare(7)
        }
      case csrCase: CSROpType => 
        csrCase match {
          case _: UopCsrrw.type  => CSROpType.csrrw
          case _: UopCsrrs.type  => CSROpType.csrrs
          case _: UopCsrrc.type  => CSROpType.csrrc
          case _: UopCsrrwi.type => CSROpType.csrrwi 
          case _: UopCsrrsi.type => CSROpType.csrrsi
          case _: UopCsrrci.type => CSROpType.csrrci
          case _ => BitPat.dontCare(7)
        }
      case fenceCase: FenceOpType =>
        fenceCase match {
          case _: UopFence.type => FenceOpType.fence
          case _: UopFencei.type => FenceOpType.fencei
          case _: UopSfence.type => FenceOpType.sfence
          case _ => BitPat.dontCare(7)
        }
      case jmpCase: JumpOpType =>
        jmpCase match {
          case _: UopJal.type => JumpOpType.jal
          case _: UopJalr.type => JumpOpType.jalr
          case _: UopAuipc.type => JumpOpType.auipc
          case _ => BitPat.dontCare(7)
        }
      case lduCase: LSUOpType =>
        lduCase match {
          case _: UopLb.type  => LSUOpType.lb
          case _: UopLh.type  => LSUOpType.lh
          case _: UopLw.type  => LSUOpType.lw
          case _: UopLd.type  => LSUOpType.ld
          case _: UopLbu.type => LSUOpType.lbu
          case _: UopLhu.type => LSUOpType.lhu
          case _: UopLwu.type => LSUOpType.lwu
          case _ => BitPat.dontCare(7)
        }
      case stuCase: LSUOpType =>
        stuCase match {
          case _: UopSb.type => LSUOpType.sb
          case _: UopSh.type => LSUOpType.sh
          case _: UopSw.type => LSUOpType.sw
          case _: UopSd.type => LSUOpType.sd
          case _ => BitPat.dontCare(7)
        }
      case mouCase: LSUOpType =>
        mouCase match {
          case _ => BitPat.dontCare(7)
        }
      case mulCase: MulOpType =>
        mulCase match {
          case _: UopMul.type     => MDUOpType.mul
          case _: UopMulh.type    => MDUOpType.mulh
          case _: UopMulhsu.type  => MDUOpType.mulhsu
          case _: UopMulhu.type   => MDUOpType.mulhu
          case _: UopMulw.type    => MDUOpType.mulw

          case _ => BitPat.dontCare(7)
        }
      case divCase: DivOpType =>
        divCase match {
          case _: UopDiv.type  => MDUOpType.div
          case _: UopDivu.type => MDUOpType.divu
          case _: UopRem.type  => MDUOpType.rem
          case _: UopRemu.type => MDUOpType.remu
          case _ => BitPat.dontCare(7)
        }
      case _ => BitPat.dontCare(7)
      }
    }

    object Imm extends ImmUopField {
      override def genTable(pattern: CPUDecodePattern): BitPat =
        if(pattern.isI)      SignExt(instr(31, 20) , XLEN)
        else if(pattern.isS) SignExt(Cat(instr(31, 25), instr(11, 7)), XLEN)
        else if(pattern.isB) SignExt(Cat(instr(31), instr(7), instr(30, 25), instr(11, 8), 0.U(1.W)) , XLEN),
        else if(pattern.isU) SignExt(Cat(instr(31, 12), 0.U(12.W)) , XLEN),
        else if(pattern.isJ) SignExt(Cat(instr(31), instr(19, 12), instr(20), instr(30, 21), 0.U(1.W)) , XLEN)
        else BitPat.dontCare(32)
    }
}