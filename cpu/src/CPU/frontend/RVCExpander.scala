// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package cpu.frontend

import chisel3._
import chisel3.util._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}

object RVCExpanderParameter {
  implicit def rwP: upickle.default.ReadWriter[RVCExpanderParameter] =
    upickle.default.macroRW
}

case class RVCExpanderParameter(xlen: Int, usingCompressed: Boolean) extends SerializableModuleParameter {
  val useAddiForMv: Boolean = false
}

class RVCExpanderInterface(parameter: RVCExpanderParameter) extends Bundle {
  val in = Input(UInt(16.W))
  val out = Output(UInt(32.W))
}

// 从rocketv修改而来
@instantiable
class RVCExpander(val parameter: RVCExpanderParameter)
    extends FixedIORawModule(new RVCExpanderInterface(parameter))
    with SerializableModule[RVCExpanderParameter] {

  val x = io.in
  val xLen = parameter.xlen
  val usingCompressed = parameter.usingCompressed
  val useAddiForMv = parameter.useAddiForMv

  def rs1p = Cat(1.U(2.W), x(9, 7)) // rs1' or rd'/rs1'
  def rs2p = Cat(1.U(2.W), x(4, 2)) // rs2' or rd'
  def rs2 = x(6, 2) // rs2
  def rd = x(11, 7) // rd/rs1
  def addi4spnImm = Cat(x(10, 7), x(12, 11), x(5), x(6), 0.U(2.W))
  def lwImm = Cat(x(5), x(12, 10), x(6), 0.U(2.W))
  def ldImm = Cat(x(6, 5), x(12, 10), 0.U(3.W))
  def lwspImm = Cat(x(3, 2), x(12), x(6, 4), 0.U(2.W))
  def ldspImm = Cat(x(4, 2), x(12), x(6, 5), 0.U(3.W))
  def swspImm = Cat(x(8, 7), x(12, 9), 0.U(2.W))
  def sdspImm = Cat(x(9, 7), x(12, 10), 0.U(3.W))
  def luiImm = Cat(Fill(15, x(12)), x(6, 2), 0.U(12.W))
  def addi16spImm = Cat(Fill(3, x(12)), x(4, 3), x(5), x(2), x(6), 0.U(4.W))
  def addiImm = Cat(Fill(7, x(12)), x(6, 2))
  def jImm = Cat(Fill(10, x(12)), x(8), x(10, 9), x(6), x(7), x(2), x(11), x(5, 3), 0.U(1.W))
  def bImm = Cat(Fill(5, x(12)), x(6, 5), x(2), x(11, 10), x(4, 3), 0.U(1.W))
  def shamt = Cat(x(12), x(6, 2))
  def x0 = 0.U(5.W)
  def ra = 1.U(5.W)
  def sp = 2.U(5.W)

  def q0 = {
    def addi4spn = {
      val opc = Mux(x(12, 5).orR, 0x13.U(7.W), 0x1f.U(7.W)) // 当uimm=0时非法，修改opcode使之成为非法指令, 下同
      Cat(addi4spnImm, sp, 0.U(3.W), rs2p, opc)
    }
    def ld = Cat(ldImm, rs1p, 3.U(3.W), rs2p, 0x03.U(7.W))
    def lw = Cat(lwImm, rs1p, 2.U(3.W), rs2p, 0x03.U(7.W))
    def fld = Cat(ldImm, rs1p, 3.U(3.W), rs2p, 0x07.U(7.W))
    def flw = {
      if (xLen == 32) Cat(lwImm, rs1p, 2.U(3.W), rs2p, 0x07.U(7.W))
      else ld
    }
    def unimp = Cat(lwImm >> 5, rs2p, rs1p, 2.U(3.W), lwImm(4, 0), 0x3f.U(7.W))
    def sd = Cat(ldImm >> 5, rs2p, rs1p, 3.U(3.W), ldImm(4, 0), 0x23.U(7.W))
    def sw = Cat(lwImm >> 5, rs2p, rs1p, 2.U(3.W), lwImm(4, 0), 0x23.U(7.W))
    def fsd = Cat(ldImm >> 5, rs2p, rs1p, 3.U(3.W), ldImm(4, 0), 0x27.U(7.W))
    def fsw = {
      if (xLen == 32) Cat(lwImm >> 5, rs2p, rs1p, 2.U(3.W), lwImm(4, 0), 0x27.U(7.W))
      else sd
    }
    Seq(addi4spn, fld, lw, flw, unimp, fsd, sw, fsw)
  }

  def q1 = {
    def addi = Cat(addiImm, rd, 0.U(3.W), rd, 0x13.U(7.W))
    def addiw = {
      val opc = Mux(rd.orR, 0x1b.U(7.W), 0x1f.U(7.W))
      Cat(addiImm, rd, 0.U(3.W), rd, opc)
    }
    def jal = {
      if (xLen == 32) Cat(jImm(20), jImm(10, 1), jImm(11), jImm(19, 12), ra, 0x6f.U(7.W))
      else addiw
    }
    def li = Cat(addiImm, x0, 0.U(3.W), rd, 0x13.U(7.W))
    def addi16sp = {
      val opc = Mux(addiImm.orR, 0x13.U(7.W), 0x1f.U(7.W))
      Cat(addi16spImm, rd, 0.U(3.W), rd, opc)
    }
    def lui = {
      val opc = Mux(addiImm.orR, 0x37.U(7.W), 0x3f.U(7.W))
      val me = Cat(luiImm(31, 12), rd, opc)
      Mux(rd === x0 || rd === sp, addi16sp, me)
    }
    def j = Cat(jImm(20), jImm(10, 1), jImm(11), jImm(19, 12), x0, 0x6f.U(7.W))
    def beqz = Cat(bImm(12), bImm(10, 5), x0, rs1p, 0.U(3.W), bImm(4, 1), bImm(11), 0x63.U(7.W))
    def bnez = Cat(bImm(12), bImm(10, 5), x0, rs1p, 1.U(3.W), bImm(4, 1), bImm(11), 0x63.U(7.W))
    def arith = {
      def srli = Cat(shamt, rs1p, 5.U(3.W), rs1p, 0x13.U(7.W))
      def srai = srli | (1 << 30).U
      def andi = Cat(addiImm, rs1p, 7.U(3.W), rs1p, 0x13.U(7.W))
      def rtype = {
        val funct = VecInit(Seq(0.U, 4.U, 6.U, 7.U, 0.U, 0.U, 2.U, 3.U))(Cat(x(12), x(6, 5)))
        val sub = Mux(x(6, 5) === 0.U, (1 << 30).U, 0.U)
        val opc = Mux(x(12), 0x3b.U(7.W), 0x33.U(7.W))
        Cat(rs2p, rs1p, funct, rs1p, opc) | sub
      }
      VecInit(Seq(srli, srai, andi, rtype))(x(11, 10))
    }
    Seq(addi, jal, li, lui, arith, j, beqz, bnez)
  }

  def q2 = {
    val load_opc = Mux(rd.orR, 0x03.U(7.W), 0x1f.U(7.W))
    def slli = Cat(shamt, rd, 1.U(3.W), rd, 0x13.U(7.W))
    def ldsp = Cat(ldspImm, sp, 3.U(3.W), rd, load_opc)
    def lwsp = Cat(lwspImm, sp, 2.U(3.W), rd, load_opc)
    def fldsp = Cat(ldspImm, sp, 3.U(3.W), rd, 0x07.U(7.W))
    def flwsp = {
      if (xLen == 32) Cat(lwspImm, sp, 2.U(3.W), rd, 0x07.U(7.W))
      else ldsp
    }
    def sdsp = Cat(sdspImm >> 5, rs2, sp, 3.U(3.W), sdspImm(4, 0), 0x23.U(7.W))
    def swsp = Cat(swspImm >> 5, rs2, sp, 2.U(3.W), swspImm(4, 0), 0x23.U(7.W))
    def fsdsp = Cat(sdspImm >> 5, rs2, sp, 3.U(3.W), sdspImm(4, 0), 0x27.U(7.W))
    def fswsp = {
      if (xLen == 32) Cat(swspImm >> 5, rs2, sp, 2.U(3.W), swspImm(4, 0), 0x27.U(7.W))
      else sdsp
    }
    def jalr = {
      val mv = {
        if (useAddiForMv) Cat(rs2, 0.U(3.W), rd, 0x13.U(7.W))
        else Cat(rs2, x0, 0.U(3.W), rd, 0x33.U(7.W))
      }
      val add = Cat(rs2, rd, 0.U(3.W), rd, 0x33.U(7.W))
      val jr = Cat(rs2, rd, 0.U(3.W), x0, 0x67.U(7.W))
      val reserved = Cat(jr >> 7, 0x1f.U(7.W))
      val jr_reserved = Mux(rd.orR, jr, reserved)
      val jr_mv = Mux(rs2.orR, mv, jr_reserved)
      val jalr = Cat(rs2, rd, 0.U(3.W), ra, 0x67.U(7.W))
      val ebreak = Cat(jr >> 7, 0x73.U(7.W))
      val jalr_ebreak = Mux(rd.orR, jalr, ebreak)
      val jalr_add = Mux(rs2.orR, add, jalr_ebreak)
      Mux(x(12), jalr_add, jr_mv)
    }
    Seq(slli, fldsp, lwsp, flwsp, jalr, fsdsp, swsp, fswsp)
  }

  def q3 = Seq.fill(8)(passthrough)

  def passthrough = x

  def decode = {
    val s = q0 ++ q1 ++ q2 ++ q3
    VecInit(s)(Cat(x(1, 0), x(15, 13)))
  }

  if (usingCompressed) {
    io.out := decode
  } else {
    io.out := passthrough
  }
}
