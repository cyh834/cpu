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
  def chiselType: UInt = UInt(7.W)
}
trait ImmUopField extends CPUDecodeFiled[UInt] with FieldName {
  def chiselType: UInt = UInt(32.W)
}

object Decoder {
  implicit def uintToBitPat(x: UInt): BitPat = BitPat(x)

  object ReadRs1 extends BoolField {
    override def genTable(pattern: CPUDecodePattern): BitPat = if (pattern.ReadRs1) y else n
  }

  object ReadRs2 extends BoolField {
    override def genTable(pattern: CPUDecodePattern): BitPat = if (pattern.ReadRs2) y else n
  }

  object WriteRd extends BoolField {
    override def genTable(pattern: CPUDecodePattern): BitPat = if (pattern.WriteRd) y else n
  }

  object Fu extends FuTypeUopField {
    override def genTable(pattern: CPUDecodePattern): BitPat =
      if (pattern.isALU) FuType.alu
      else if (pattern.isBRU) FuType.bru
      else if (pattern.isCSR) FuType.csr
      else if (pattern.isDIV) FuType.div
      else if (pattern.isFENCE) FuType.fence
      else if (pattern.isJMP) FuType.jmp
      else if (pattern.isLDU) FuType.ldu
      else if (pattern.isMOU) FuType.mou
      else if (pattern.isMUL) FuType.mul
      else if (pattern.isSTU) FuType.stu
      else BitPat.dontCare(4)
  }

//TODO: Too long
  object FuOp extends FuOpTypeUopField {
    override def genTable(pattern: CPUDecodePattern): BitPat = pattern.FUOpType match {
      case aluCase: AluUOP =>
        aluCase match {
          case _: Uoplui.type  => ALUOpType.add
          case _: Uopaddi.type => ALUOpType.add
          case _: Uopandi.type => ALUOpType.and
          case _: Uopori.type  => ALUOpType.or
          case _: Uopxori.type => ALUOpType.xor
          case _: Uopslti.type => ALUOpType.slt
          case _: Uopsltiu.type => ALUOpType.sltu
          case _: Uopsll.type => ALUOpType.sll
          case _: Uopadd.type => ALUOpType.add
          case _: Uopsub.type => ALUOpType.sub
          case _: Uopslt.type => ALUOpType.slt
          case _: Uopsltu.type => ALUOpType.sltu
          case _: Uopand.type => ALUOpType.and
          case _: Uopor.type => ALUOpType.or
          case _: Uopxor.type => ALUOpType.xor
          case _: Uopsra.type => ALUOpType.sra
          case _: Uopsrl.type => ALUOpType.srl
          case _: Uopslli.type => ALUOpType.sll
          case _: Uopsrli.type => ALUOpType.srl
          case _: Uopsrai.type => ALUOpType.sra
          case _: Uopaddiw.type => ALUOpType.addw
          case _: Uopslliw.type => ALUOpType.sllw
          case _: Uopsraiw.type => ALUOpType.sraw
          case _: Uopsrliw.type => ALUOpType.srlw
          case _: Uopaddw.type => ALUOpType.addw
          case _: Uopsubw.type => ALUOpType.subw
          case _: Uopsllw.type => ALUOpType.sllw
          case _: Uopsraw.type => ALUOpType.sraw
          case _: Uopsrlw.type => ALUOpType.srlw
          case _ => BitPat.dontCare(7)
        }
      case bruCase: BruUOP =>
        bruCase match {
          case _: Uopbeq.type => BRUOpType.beq
          case _: Uopbne.type => BRUOpType.bne
          case _: Uopblt.type => BRUOpType.blt
          case _: Uopbge.type => BRUOpType.bge
          case _: Uopbltu.type => BRUOpType.bltu
          case _: Uopbgeu.type => BRUOpType.bgeu
          case _ => BitPat.dontCare(7)
        }
      case csrCase: CsrUOP =>
        csrCase match {
          case _: Uopcsrrw.type => CSROpType.csrrw
          case _: Uopcsrrs.type => CSROpType.csrrs
          case _: Uopcsrrc.type => CSROpType.csrrc
          case _: Uopcsrrwi.type => CSROpType.csrrwi
          case _: Uopcsrrsi.type => CSROpType.csrrsi
          case _: Uopcsrrci.type => CSROpType.csrrci
          case _: Uopecall.type => CSROpType.jmp
          case _: Uopebreak.type => CSROpType.jmp
          case _: Uopmret.type => CSROpType.jmp
          case _: Uopsret.type => CSROpType.jmp
          case _: Uopwfi.type => CSROpType.wfi
          case _ => BitPat.dontCare(7)
        }
      case fenceCase: FenceUOP =>
        fenceCase match {
          case _: Uopfence.type => FenceOpType.fence
          case _: Uopfencei.type => FenceOpType.fencei
          case _: Uopsfencevma.type => FenceOpType.sfence
          case _ => BitPat.dontCare(7)
        }
      case jmpCase: JmpUOP =>
        jmpCase match {
          case _: Uopjal.type => JumpOpType.jal
          case _: Uopjalr.type => JumpOpType.jalr
          case _: Uopauipc.type => JumpOpType.auipc
          case _ => BitPat.dontCare(7)
        }
      case lduCase: LsuUOP =>
        lduCase match {
          case _: Uoplb.type => LSUOpType.lb
          case _: Uoplh.type => LSUOpType.lh
          case _: Uoplw.type => LSUOpType.lw
          case _: Uopld.type => LSUOpType.ld
          case _: Uoplbu.type => LSUOpType.lbu
          case _: Uoplhu.type => LSUOpType.lhu
          case _: Uoplwu.type => LSUOpType.lwu
          case _ => BitPat.dontCare(7)
        }
      case stuCase: StuUOP =>
        stuCase match {
          case _: Uopsb.type => LSUOpType.sb
          case _: Uopsh.type => LSUOpType.sh
          case _: Uopsw.type => LSUOpType.sw
          case _: Uopsd.type => LSUOpType.sd
          case _ => BitPat.dontCare(7)
        }
      case mouCase: MouUOP =>
        mouCase match {
          case _: Uoplrw.type => LSUOpType.lr_w
          case _: Uopscw.type => LSUOpType.sc_w
          case _: Uopamoswapw.type => LSUOpType.amoswap_w
          case _: Uopamoaddw.type => LSUOpType.amoadd_w
          case _: Uopamoxorw.type => LSUOpType.amoxor_w
          case _: Uopamoandw.type => LSUOpType.amoand_w 
          case _: Uopamoorw.type => LSUOpType.amoor_w
          case _: Uopamominw.type => LSUOpType.amomin_w
          case _: Uopamomaxw.type => LSUOpType.amomax_w
          case _: Uopamominuw.type => LSUOpType.amominu_w
          case _: Uopamomaxuw.type => LSUOpType.amomaxu_w
          case _: Uoplrd.type => LSUOpType.lr_d
          case _: Uopscd.type => LSUOpType.sc_d
          case _: Uopamoswapd.type => LSUOpType.amoswap_d
          case _: Uopamoaddd.type => LSUOpType.amoadd_d
          case _: Uopamoxord.type => LSUOpType.amoxor_d
          case _: Uopamoandd.type => LSUOpType.amoand_d
          case _: Uopamoord.type => LSUOpType.amoor_d
          case _: Uopamomind.type => LSUOpType.amomin_d
          case _: Uopamomaxd.type => LSUOpType.amomax_d
          case _: Uopamominud.type => LSUOpType.amominu_d
          case _: Uopamomaxud.type => LSUOpType.amomaxu_d
          case _ => BitPat.dontCare(7)
        }
      case mulCase: MulUOP =>
        mulCase match {
          case _: Uopmul.type => MDUOpType.mul
          case _: Uopmulh.type => MDUOpType.mulh
          case _: Uopmulhsu.type => MDUOpType.mulhsu
          case _: Uopmulhu.type => MDUOpType.mulhu
          case _: Uopmulw.type => MDUOpType.mulw
          case _ => BitPat.dontCare(7)
        }
      case divCase: DivUOP =>
        divCase match {
          case _: Uopdiv.type => DIVOpType.div
          case _: Uopdivu.type => DIVOpType.divu
          case _: Uoprem.type => DIVOpType.rem
          case _: Uopremu.type => DIVOpType.remu
          case _: Uopdivw.type => DIVOpType.divw
          case _: Uopdivuw.type => DIVOpType.divuw
          case _: Uopremw.type => DIVOpType.remw
          case _: Uopremuw.type => DIVOpType.remuw
          case _ => BitPat.dontCare(7)
        }
      case _ => BitPat.dontCare(7)
    }
  }

  object Imm extends ImmUopField {
    val XLEN = 64 //TODO 
    override def genTable(pattern: CPUDecodePattern): BitPat =
      if (pattern.isI) SignExt(instr(31, 20), XLEN)
      else if (pattern.isS) SignExt(Cat(instr(31, 25), instr(11, 7)), XLEN)
      else if (pattern.isB) SignExt(Cat(instr(31), instr(7), instr(30, 25), instr(11, 8), 0.U(1.W)), XLEN)
      else if (pattern.isU) SignExt(Cat(instr(31, 12), 0.U(12.W)), XLEN)
      else if (pattern.isJ) SignExt(Cat(instr(31), instr(19, 12), instr(20), instr(30, 21), 0.U(1.W)), XLEN)
      else BitPat.dontCare(32)
  }
}
