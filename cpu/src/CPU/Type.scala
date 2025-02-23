package cpu

import chisel3._
import chisel3.util._

//执行模块类型
object FuType {
  def width = 4

  def alu = "b0000".U(width.W)
  def brh = "b1001".U(width.W)

  def ldu = "b0001".U(width.W)
  def stu = "b0010".U(width.W)
  def mou = "b0011".U(width.W)

  def mul = "b0100".U(width.W)
  def div = "b0101".U(width.W)

  def jmp = "b0110".U(width.W)

  def fence = "b0111".U(width.W)
  def csr = "b1000".U(width.W)

  def dontCare = BitPat.dontCare(width)
  def apply() = UInt(width.W)

  def isjmp(fu: UInt) = fu === jmp
  def isbrh(fu: UInt) = fu === brh
  def isldu(fu: UInt) = fu === ldu
  def isstu(fu: UInt) = fu === stu
  def islsu(fu: UInt) = isldu(fu) || isstu(fu)
}

object FuOpType {
  def width = 7
  def dontCare = BitPat.dontCare(width)
  def apply() = UInt(7.W)
}

object LSUOpType {
  // load pipeline
  // bit encoding: | load 0 | is unsigned(1bit) | size(2bit) |
  def lb = "b0000".U(FuOpType.width.W)
  def lh = "b0001".U(FuOpType.width.W)
  def lw = "b0010".U(FuOpType.width.W)
  def ld = "b0011".U(FuOpType.width.W)
  def lbu = "b0100".U(FuOpType.width.W)
  def lhu = "b0101".U(FuOpType.width.W)
  def lwu = "b0110".U(FuOpType.width.W)

  def size(func:           UInt) = func(1, 0)
  def loadIsUnsigned(func: UInt) = func(2)

  // store pipeline
  // bit encoding: | store 00 | size(2bit) |
  def sb = "b0000".U(FuOpType.width.W)
  def sh = "b0001".U(FuOpType.width.W)
  def sw = "b0010".U(FuOpType.width.W)
  def sd = "b0011".U(FuOpType.width.W)

  // atomics
  // bit(1, 0) are size
  // since atomics use a different fu type
  // so we can safely reuse other load/store's encodings
  // bit encoding: | optype(4bit) | size (2bit) |
  def lr_w = "b000010".U(FuOpType.width.W)
  def sc_w = "b000110".U(FuOpType.width.W)
  def amoswap_w = "b001010".U(FuOpType.width.W)
  def amoadd_w = "b001110".U(FuOpType.width.W)
  def amoxor_w = "b010010".U(FuOpType.width.W)
  def amoand_w = "b010110".U(FuOpType.width.W)
  def amoor_w = "b011010".U(FuOpType.width.W)
  def amomin_w = "b011110".U(FuOpType.width.W)
  def amomax_w = "b100010".U(FuOpType.width.W)
  def amominu_w = "b100110".U(FuOpType.width.W)
  def amomaxu_w = "b101010".U(FuOpType.width.W)

  def lr_d = "b000011".U(FuOpType.width.W)
  def sc_d = "b000111".U(FuOpType.width.W)
  def amoswap_d = "b001011".U(FuOpType.width.W)
  def amoadd_d = "b001111".U(FuOpType.width.W)
  def amoxor_d = "b010011".U(FuOpType.width.W)
  def amoand_d = "b010111".U(FuOpType.width.W)
  def amoor_d = "b011011".U(FuOpType.width.W)
  def amomin_d = "b011111".U(FuOpType.width.W)
  def amomax_d = "b100011".U(FuOpType.width.W)
  def amominu_d = "b100111".U(FuOpType.width.W)
  def amomaxu_d = "b101011".U(FuOpType.width.W)

  def apply() = UInt(FuOpType.width.W)
}

//TODO: 更合理的编码?
object ALUOpType {
  // shift optype
  def sll = "b000_0000".U(FuOpType.width.W) // sll:     src1 << src2
  def srl = "b000_0001".U(FuOpType.width.W) // srl:     src1 >> src2
  def sra = "b000_0010".U(FuOpType.width.W) // sra:     src1 >> src2 (arithmetic)

  // add optype
  def add = "b000_0011".U(FuOpType.width.W) // add:     src1 + src2

  // logic optype
  def and = "b000_0100".U(FuOpType.width.W) // and:     src1 & src2
  def or = "b000_0101".U(FuOpType.width.W) // or:      src1 | src2
  def xor = "b000_0110".U(FuOpType.width.W) // xor:     src1 ^ src2

  // sub optype
  def sltu = "b010_0111".U(FuOpType.width.W) // sltu:    src1 < src2 (unsigned)
  def slt = "b010_1000".U(FuOpType.width.W) // slt:     src1 < src2 (signed)
  def sub = "b010_0011".U(FuOpType.width.W) // sub:     src1 - src2

  // RV64 32bit optype
  def sllw = "b100_0000".U(FuOpType.width.W) // sllw:    SEXT((src1 << src2[4:0])[31:0])
  def srlw = "b100_0001".U(FuOpType.width.W) // srlw:    SEXT((src1[31:0] >> src2[4:0])[31:0])
  def sraw = "b100_0010".U(FuOpType.width.W) // sraw:    SEXT((src1[31:0] >> src2[4:0])[31:0]) (arithmetic)

  def addw = "b100_0011".U(FuOpType.width.W) // addw:    SEXT((src1 + src2)[31:0])
  def subw = "b110_0011".U(FuOpType.width.W) // subw:    SEXT((src1 - src2)[31:0])

  def isWordOp(func: UInt) = func(6)
  def isSubOp(func:  UInt) = func(5)

  def apply() = UInt(FuOpType.width.W)
}

object BRUOpType {
  // branch
  def beq = "b000_000".U(FuOpType.width.W)
  def bne = "b000_001".U(FuOpType.width.W)
  def blt = "b000_100".U(FuOpType.width.W)
  def bge = "b000_101".U(FuOpType.width.W)
  def bltu = "b001_000".U(FuOpType.width.W)
  def bgeu = "b001_001".U(FuOpType.width.W)

  def getBranchType(func:  UInt) = func(3, 1)
  def isBranchInvert(func: UInt) = func(0)
}

object JumpOpType {
  def jal = "b00".U(FuOpType.width.W) // pc += SEXT(offset)
  def jalr = "b01".U(FuOpType.width.W) // pc = (src + SEXT(offset)) & ~1
  def auipc = "b10".U(FuOpType.width.W)
//    def call = "b11_011".U(FuOpType.width.W)
//    def ret  = "b11_100".U(FuOpType.width.W)
  def isJalr(op:  UInt) = op(0)
  def isAuipc(op: UInt) = op(1)
}

object CSROpType {
  //               | func3|
  def jmp = "b010_000".U(FuOpType.width.W) // ECALL, EBREAK, SRET, MRET, ...
  def wfi = "b100_000".U(FuOpType.width.W)
  def csrrw = "b001_001".U(FuOpType.width.W)
  def csrrs = "b001_010".U(FuOpType.width.W)
  def csrrc = "b001_011".U(FuOpType.width.W)
  def csrrwi = "b001_101".U(FuOpType.width.W)
  def csrrsi = "b001_110".U(FuOpType.width.W)
  def csrrci = "b001_111".U(FuOpType.width.W)

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
  def fence = "b10000".U(FuOpType.width.W)
  def sfence = "b10001".U(FuOpType.width.W)
  def fencei = "b10010".U(FuOpType.width.W)
  def nofence = "b00000".U(FuOpType.width.W)
}

object MDUOpType {
  // mul
  // bit encoding: | type (2bit) | isWord(1bit) | opcode(2bit) |
  def mul = "b00000".U(FuOpType.width.W)
  def mulh = "b00001".U(FuOpType.width.W)
  def mulhsu = "b00010".U(FuOpType.width.W)
  def mulhu = "b00011".U(FuOpType.width.W)
  def mulw = "b00100".U(FuOpType.width.W)

  def mulw7 = "b01100".U(FuOpType.width.W)

  // div
  // bit encoding: | type (2bit) | isWord(1bit) | isSign(1bit) | opcode(1bit) |
  def div = "b10000".U(FuOpType.width.W)
  def divu = "b10010".U(FuOpType.width.W)
  def rem = "b10001".U(FuOpType.width.W)
  def remu = "b10011".U(FuOpType.width.W)

  def divw = "b10100".U(FuOpType.width.W)
  def divuw = "b10110".U(FuOpType.width.W)
  def remw = "b10101".U(FuOpType.width.W)
  def remuw = "b10111".U(FuOpType.width.W)

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

object InstrType {
  val width = 3

  def R = "b000".U(width.W)
  def I = "b001".U(width.W)
  def S = "b010".U(width.W)
  def B = "b011".U(width.W)
  def U = "b100".U(width.W)
  def J = "b101".U(width.W)
  def INVALID_INSTR = "b0110".U(width.W)

  def dontCare = BitPat.dontCare(width)
  // def X      = BitPat("b000")
  def apply() = UInt(width.W)

}
