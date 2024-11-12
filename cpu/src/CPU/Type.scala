package cpu

import chisel3._
import chisel3.util._

//执行模块类型
object FuType {
  def num = 9

  def alu = "b0000".U
  def brh = "b1001".U

  def ldu = "b0001".U
  def stu = "b0010".U
  def mou = "b0011".U

  def mul = "b0100".U
  def div = "b0101".U

  def jmp = "b0110".U

  def fence = "b0111".U
  def csr = "b1000".U

  def apply() = UInt(log2Up(num).W)
  def isjmp(fu: UInt) = fu === jmp
  def isbrh(fu: UInt) = fu === brh
}

object FuOpType {
  def apply() = UInt(7.W)
}

object LSUOpType {
  // load pipeline
  // bit encoding: | load 0 | is unsigned(1bit) | size(2bit) |
  def lb = "b0000".U
  def lh = "b0001".U
  def lw = "b0010".U
  def ld = "b0011".U
  def lbu = "b0100".U
  def lhu = "b0101".U
  def lwu = "b0110".U

  // store pipeline
  // bit encoding: | store 00 | size(2bit) |
  def sb = "b0000".U
  def sh = "b0001".U
  def sw = "b0010".U
  def sd = "b0011".U

  // atomics
  // bit(1, 0) are size
  // since atomics use a different fu type
  // so we can safely reuse other load/store's encodings
  // bit encoding: | optype(4bit) | size (2bit) |
  def lr_w = "b000010".U
  def sc_w = "b000110".U
  def amoswap_w = "b001010".U
  def amoadd_w = "b001110".U
  def amoxor_w = "b010010".U
  def amoand_w = "b010110".U
  def amoor_w = "b011010".U
  def amomin_w = "b011110".U
  def amomax_w = "b100010".U
  def amominu_w = "b100110".U
  def amomaxu_w = "b101010".U

  def lr_d = "b000011".U
  def sc_d = "b000111".U
  def amoswap_d = "b001011".U
  def amoadd_d = "b001111".U
  def amoxor_d = "b010011".U
  def amoand_d = "b010111".U
  def amoor_d = "b011011".U
  def amomin_d = "b011111".U
  def amomax_d = "b100011".U
  def amominu_d = "b100111".U
  def amomaxu_d = "b101011".U

  def apply() = UInt(6.W)
}

//TODO: 更合理的编码?
object ALUOpType {
  // shift optype
  def sll = "b000_0000".U // sll:     src1 << src2
  def srl = "b000_0001".U // srl:     src1 >> src2
  def sra = "b000_0010".U // sra:     src1 >> src2 (arithmetic)

  // add optype
  def add = "b000_0011".U // add:     src1 + src2

  // logic optype
  def and = "b000_0100".U // and:     src1 & src2
  def or = "b000_0101".U // or:      src1 | src2
  def xor = "b000_0110".U // xor:     src1 ^ src2

  // sub optype
  def sub = "b010_0000".U // sub:     src1 - src2
  def sltu = "b010_0001".U // sltu:    src1 < src2 (unsigned)
  def slt = "b010_0010".U // slt:     src1 < src2 (signed)

  // RV64 32bit optyp0
  def addw = "b100_0000".U // addw:    SEXT((src1 + src2)[31:0])
  def subw = "b100_0001".U // subw:    SEXT((src1 - src2)[31:0])

  def sllw = "b100_0100".U // sllw:    SEXT((src1 << src2[4:0])[31:0])
  def srlw = "b100_0101".U // srlw:    SEXT((src1[31:0] >> src2[4:0])[31:0])
  def sraw = "b100_0110".U // sraw:    SEXT((src1[31:0] >> src2[4:0])[31:0]) (arithmetic)

  def isWordOp(func: UInt) = func(6)
  def isSubOp(func:  UInt) = func(5)

  def FuOpTypeWidth = 7
  def apply() = UInt(FuOpTypeWidth.W)
}

object BRUOpType {
  // branch
  def beq = "b000_000".U
  def bne = "b000_001".U
  def blt = "b000_100".U
  def bge = "b000_101".U
  def bltu = "b001_000".U
  def bgeu = "b001_001".U

  def getBranchType(func:  UInt) = func(3, 1)
  def isBranchInvert(func: UInt) = func(0)
}

object JumpOpType {
  def jal = "b00".U // pc += SEXT(offset)
  def jalr = "b01".U // pc = (src + SEXT(offset)) & ~1
  def auipc = "b10".U
//    def call = "b11_011".U
//    def ret  = "b11_100".U
  def isJalr(op:  UInt) = op(0)
  def isAuipc(op: UInt) = op(1)
}

object CSROpType {
  //               | func3|
  def jmp = "b010_000".U // ECALL, EBREAK, SRET, MRET, ...
  def wfi = "b100_000".U
  def csrrw = "b001_001".U
  def csrrs = "b001_010".U
  def csrrc = "b001_011".U
  def csrrwi = "b001_101".U
  def csrrsi = "b001_110".U
  def csrrci = "b001_111".U

  def isSystemOp(op:  UInt): Bool = op(4)
  def isWfi(op:       UInt): Bool = op(5)
  def isCsrAccess(op: UInt): Bool = op(3)
  def isReadOnly(op:  UInt): Bool = op(3) && op(2, 0) === 0.U
  def notReadOnly(op: UInt): Bool = op(3) && op(2, 0) =/= 0.U
  def isCSRRW(op:     UInt): Bool = op(3) && op(1, 0) === "b01".U
  def isCSRRSorRC(op: UInt): Bool = op(3) && op(1)

  def getCSROp(op: UInt) = op(1, 0)
  def needImm(op:  UInt) = op(2)

  def getFunc3(op: UInt) = op(2, 0)
}

object FenceOpType {
  def fence = "b10000".U
  def sfence = "b10001".U
  def fencei = "b10010".U
  def nofence = "b00000".U
}

object MDUOpType {
  // mul
  // bit encoding: | type (2bit) | isWord(1bit) | opcode(2bit) |
  def mul = "b00000".U
  def mulh = "b00001".U
  def mulhsu = "b00010".U
  def mulhu = "b00011".U
  def mulw = "b00100".U

  def mulw7 = "b01100".U

  // div
  // bit encoding: | type (2bit) | isWord(1bit) | isSign(1bit) | opcode(1bit) |
  def div = "b10000".U
  def divu = "b10010".U
  def rem = "b10001".U
  def remu = "b10011".U

  def divw = "b10100".U
  def divuw = "b10110".U
  def remw = "b10101".U
  def remuw = "b10111".U

  def isMul(op: UInt) = !op(4)
  def isDiv(op: UInt) = op(4)

  def isDivSign(op: UInt) = isDiv(op) && !op(1)
  def isW(op:       UInt) = op(2)
  def isH(op:       UInt) = (isDiv(op) && op(0)) || (isMul(op) && op(1, 0) =/= 0.U)
  def getMulOp(op:  UInt) = op(1, 0)
}

object ExceptionVec {
  val ExceptionVecSize = 24
  def apply() = Vec(ExceptionVecSize, Bool())
}

//object InstrType {
//  def R = "b000".U
//  def I = "b001".U
//  def S = "b010".U
//  def B = "b011".U
//  def U = "b100".U
//  def J = "b101".U
//  def INVALID_INSTR = "b0110".U
//
//  // def X      = BitPat("b000")
//  def apply() = UInt(3.W)
//
//}
