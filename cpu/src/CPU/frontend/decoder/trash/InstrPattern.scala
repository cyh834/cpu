//package core.frontend.decode
//
//import chisel3._
//import chisel3.util._
//import chisel3.util.experimental.decode._
//import chisel3.util.BitPat.bitPatToUInt
//
//import core.frontend.decode.Instructions._
//import core._
//
////更精简化?
//
//
//case class InstrPattern(
//  instr: BitPat,
//  src1: UInt,  src2: UInt,
//  fu: UInt, fuOp: UInt, InstrType: UInt,
//  xWen: Boolean = false, 
//  CPUTrap: Boolean = false,
//  noSpec: Boolean = false,
//  blockBack: Boolean = false,
//  flushPipe: Boolean = false,
//) {
//  implicit def uintToBitPat(x: UInt): BitPat = BitPat(x)
//  def generate() : (BitPat, List[BitPat]) = {
//    instr -> List (src1, src2, fu, fuOp, InstrType, xWen.B, CPUTrap.B, noSpec.B, blockBack.B, flushPipe.B)
//  }
//}
//
//object INVALID_INSTR {
//  //def X = BitPat("b0")
//  def X = 0.U
//  def Error = List(X, X, X, X, InstrType.INVALID_INSTR, X, X, X, X, X)
//}
//
//object RV32I {
//  val table = Array(
//    InstrPattern(LUI  , SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.add   , InstrType.U, xWen = true),
//
//    InstrPattern(AUIPC, SrcType.pc , SrcType.imm, FuType.jmp, JumpOpType.auipc, InstrType.U, xWen = true),
//    InstrPattern(JAL  , SrcType.pc , SrcType.imm, FuType.jmp, JumpOpType.jal  , InstrType.J, xWen = true),
//    InstrPattern(JALR , SrcType.reg, SrcType.imm, FuType.jmp, JumpOpType.jalr , InstrType.I, xWen = true),
//
//    InstrPattern(BEQ  , SrcType.reg, SrcType.reg, FuType.brh, BRUOpType.beq   , InstrType.B          ),
//    InstrPattern(BNE  , SrcType.reg, SrcType.reg, FuType.brh, BRUOpType.bne   , InstrType.B          ),
//    InstrPattern(BLT  , SrcType.reg, SrcType.reg, FuType.brh, BRUOpType.blt   , InstrType.B          ),
//    InstrPattern(BGE  , SrcType.reg, SrcType.reg, FuType.brh, BRUOpType.bge   , InstrType.B          ),
//    InstrPattern(BLTU , SrcType.reg, SrcType.reg, FuType.brh, BRUOpType.bltu  , InstrType.B          ),
//    InstrPattern(BGEU , SrcType.reg, SrcType.reg, FuType.brh, BRUOpType.bgeu  , InstrType.B          ),
//
//    InstrPattern(LB   , SrcType.reg, SrcType.imm, FuType.ldu, LSUOpType.lb    , InstrType.I, xWen = true),
//    InstrPattern(LH   , SrcType.reg, SrcType.imm, FuType.ldu, LSUOpType.lh    , InstrType.I, xWen = true),
//    InstrPattern(LW   , SrcType.reg, SrcType.imm, FuType.ldu, LSUOpType.lw    , InstrType.I, xWen = true),
//    InstrPattern(LBU  , SrcType.reg, SrcType.imm, FuType.ldu, LSUOpType.lbu   , InstrType.I, xWen = true),
//    InstrPattern(LHU  , SrcType.reg, SrcType.imm, FuType.ldu, LSUOpType.lhu   , InstrType.I, xWen = true),
//
//    InstrPattern(SB   , SrcType.reg, SrcType.reg, FuType.stu, LSUOpType.sb    , InstrType.S          ),
//    InstrPattern(SH   , SrcType.reg, SrcType.reg, FuType.stu, LSUOpType.sh    , InstrType.S          ),
//    InstrPattern(SW   , SrcType.reg, SrcType.reg, FuType.stu, LSUOpType.sw    , InstrType.S          ),
//
//    InstrPattern(ADDI , SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.add   , InstrType.I, xWen = true),
//
//    InstrPattern(SLTI , SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.slt   , InstrType.I, xWen = true),
//    InstrPattern(SLTIU, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.sltu  , InstrType.I, xWen = true),
//    InstrPattern(XORI , SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.xor   , InstrType.I, xWen = true),
//    InstrPattern(ORI  , SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.or    , InstrType.I, xWen = true),
//    InstrPattern(ANDI , SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.and   , InstrType.I, xWen = true),
//    InstrPattern(SLLI , SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.sll   , InstrType.I, xWen = true),
//    InstrPattern(SRLI , SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.srl   , InstrType.I, xWen = true),
//    InstrPattern(SRAI , SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.sra   , InstrType.I, xWen = true),
//
//    InstrPattern(ADD  , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.add   , InstrType.R, xWen = true),
//    InstrPattern(SUB  , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.sub   , InstrType.R, xWen = true),
//    InstrPattern(SLL  , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.sll   , InstrType.R, xWen = true),
//    InstrPattern(SLT  , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.slt   , InstrType.R, xWen = true),
//    InstrPattern(SLTU , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.sltu  , InstrType.R, xWen = true),
//    InstrPattern(XOR  , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.xor   , InstrType.R, xWen = true),
//    InstrPattern(SRL  , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.srl   , InstrType.R, xWen = true),
//    InstrPattern(SRA  , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.sra   , InstrType.R, xWen = true),
//    InstrPattern(OR   , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.or    , InstrType.R, xWen = true),
//    InstrPattern(AND  , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.and   , InstrType.R, xWen = true),
//
//
//    //Privileged
//    InstrPattern(FENCE     , SrcType.pc , SrcType.imm, FuType.fence, FenceOpType.fence , InstrType.I, noSpec = true, blockBack = true, flushPipe = true),
//    InstrPattern(ECALL     , SrcType.reg, SrcType.imm, FuType.csr, CSROpType.jmp, InstrType.I, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(EBREAK    , SrcType.reg, SrcType.imm, FuType.csr, CSROpType.jmp, InstrType.I, xWen = true, noSpec = true, blockBack = true),
//  )
//}
//
//object RVPrivileged{
//  val table = Array(
//    InstrPattern(MRET      , SrcType.reg, SrcType.imm, FuType.csr  , CSROpType.jmp     , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(SRET      , SrcType.reg, SrcType.imm, FuType.csr  , CSROpType.jmp     , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(WFI       , SrcType.pc , SrcType.imm, FuType.csr  , CSROpType.wfi     , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(SFENCE_VMA, SrcType.reg, SrcType.reg, FuType.fence, FenceOpType.sfence, InstrType.R, noSpec = true, blockBack = true, flushPipe = true),
//  )
//}
//
//object RVZicsr{
//  val table = Array(
//    InstrPattern(CSRRW , SrcType.reg, SrcType.imm, FuType.csr, CSROpType.wrt , InstrType.I, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(CSRRS , SrcType.reg, SrcType.imm, FuType.csr, CSROpType.set , InstrType.I, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(CSRRC , SrcType.reg, SrcType.imm, FuType.csr, CSROpType.clr , InstrType.I, xWen = true, noSpec = true, blockBack = true),
//
//    InstrPattern(CSRRWI, SrcType.reg, SrcType.imm, FuType.csr, CSROpType.wrti, InstrType.I, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(CSRRSI, SrcType.reg, SrcType.imm, FuType.csr, CSROpType.seti, InstrType.I, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(CSRRCI, SrcType.reg, SrcType.imm, FuType.csr, CSROpType.clri, InstrType.I, xWen = true, noSpec = true, blockBack = true),
//  )
//}
//
//object RVZifencei{
//  val table = Array(
//    InstrPattern(FENCE_I, SrcType.pc , SrcType.imm, FuType.fence, FenceOpType.fencei, InstrType.I, noSpec = true, blockBack = true, flushPipe = true),
//  )
//}
//
//object RV64I {
//  val table = Array(
//    InstrPattern(LWU  , SrcType.reg, SrcType.imm, FuType.ldu, LSUOpType.lwu , InstrType.I, xWen = true),
//    InstrPattern(LD   , SrcType.reg, SrcType.imm, FuType.ldu, LSUOpType.ld  , InstrType.I, xWen = true),
//    InstrPattern(SD   , SrcType.reg, SrcType.reg, FuType.stu, LSUOpType.sd  , InstrType.S          ),
//
//    //slli, srli, srai
//    InstrPattern(ADDIW, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.addw, InstrType.I, xWen = true),
//    InstrPattern(SLLIW, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.sllw, InstrType.I, xWen = true),
//    InstrPattern(SRLIW, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.srlw, InstrType.I, xWen = true),
//    InstrPattern(SRAIW, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.sraw, InstrType.I, xWen = true),
//    InstrPattern(ADDW , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.addw, InstrType.R, xWen = true),
//    InstrPattern(SUBW , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.subw, InstrType.R, xWen = true),
//    InstrPattern(SLLW , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.sllw, InstrType.R, xWen = true),
//    InstrPattern(SRLW , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.srlw, InstrType.R, xWen = true),
//    InstrPattern(SRAW , SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.sraw, InstrType.R, xWen = true),
//  ) ++ RV32I.table
//}
//
//object RV32M {
//  val table = Array(
//    InstrPattern(MUL   , SrcType.reg, SrcType.reg, FuType.mul, MDUOpType.mul   , InstrType.R, xWen = true),
//    InstrPattern(MULH  , SrcType.reg, SrcType.reg, FuType.mul, MDUOpType.mulh  , InstrType.R, xWen = true),
//    InstrPattern(MULHSU, SrcType.reg, SrcType.reg, FuType.mul, MDUOpType.mulhsu, InstrType.R, xWen = true),
//    InstrPattern(MULHU , SrcType.reg, SrcType.reg, FuType.mul, MDUOpType.mulhu , InstrType.R, xWen = true),
//
//    InstrPattern(DIV   , SrcType.reg, SrcType.reg, FuType.div, MDUOpType.div   , InstrType.R, xWen = true),
//    InstrPattern(DIVU  , SrcType.reg, SrcType.reg, FuType.div, MDUOpType.divu  , InstrType.R, xWen = true),
//    InstrPattern(REM   , SrcType.reg, SrcType.reg, FuType.div, MDUOpType.rem   , InstrType.R, xWen = true),
//    InstrPattern(REMU  , SrcType.reg, SrcType.reg, FuType.div, MDUOpType.remu  , InstrType.R, xWen = true),
//  )
//}
//
//object RV64M{
//  val table = Array(
//    InstrPattern(MULW , SrcType.reg, SrcType.reg, FuType.mul, MDUOpType.mulw  , InstrType.R, xWen = true),
//    InstrPattern(DIVW , SrcType.reg, SrcType.reg, FuType.div, MDUOpType.divw  , InstrType.R, xWen = true),
//    InstrPattern(DIVUW, SrcType.reg, SrcType.reg, FuType.div, MDUOpType.divuw , InstrType.R, xWen = true),
//    InstrPattern(REMW , SrcType.reg, SrcType.reg, FuType.div, MDUOpType.remw  , InstrType.R, xWen = true),
//    InstrPattern(REMUW, SrcType.reg, SrcType.reg, FuType.div, MDUOpType.remuw , InstrType.R, xWen = true),
//  ) ++ RV32M.table
//}
//
//object RV32A {
//  val table = Array(
//    InstrPattern(LR_W     , SrcType.reg, SrcType.imm, FuType.mou, LSUOpType.lr_w, InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(SC_W     , SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.sc_w, InstrType.R, xWen = true, noSpec = true, blockBack = true),
//
//    InstrPattern(AMOSWAP_W, SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amoswap_w, InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOADD_W , SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amoadd_w , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOXOR_W , SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amoxor_w , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOAND_W , SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amoand_w , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOOR_W  , SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amoor_w  , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOMIN_W , SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amomin_w , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOMAX_W , SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amomax_w , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOMINU_W, SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amominu_w, InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOMAXU_W, SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amomaxu_w, InstrType.R, xWen = true, noSpec = true, blockBack = true),
//
//  )
//}
//
//object RV64A {
//  val table = Array(
//    InstrPattern(LR_D     , SrcType.reg, SrcType.imm, FuType.mou, LSUOpType.lr_d     , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(SC_D     , SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.sc_d     , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//
//    InstrPattern(AMOSWAP_D, SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amoswap_d, InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOADD_D , SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amoadd_d , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOXOR_D , SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amoxor_d , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOAND_D , SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amoand_d , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOOR_D  , SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amoor_d  , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOMIN_D , SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amomin_d , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOMAX_D , SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amomax_d , InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOMINU_D, SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amominu_d, InstrType.R, xWen = true, noSpec = true, blockBack = true),
//    InstrPattern(AMOMAXU_D, SrcType.reg, SrcType.reg, FuType.mou, LSUOpType.amomaxu_d, InstrType.R, xWen = true, noSpec = true, blockBack = true),
//  ) ++ RV32A.table
//}
//
////object RV32C {
////  val table = Arr
////}
//
//object Trap{
//  def TRAP = BitPat("b000000000000?????000000001101011")
//  val table = Array(
//    InstrPattern(TRAP    , SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.add, InstrType.I, xWen = true, CPUTrap = true, noSpec = true, blockBack = true)
//  )
//}
//
//object RISCV extends HasCoreParameter{
//  val RVI = if (XLEN == 64) RV64I.table else RV32I.table
//  val RVM = if (XLEN == 64) RV64M.table else RV32M.table
//  val RVA = if (XLEN == 64) RV64A.table else RV32A.table
//
//  val decodeArray = 
//    RVI ++ 
//    (if(HasMExtension) RVM else Array()) ++ 
//    (if(HasAExtension) RVA else Array()) ++ 
//    RVZicsr.table ++ RVZifencei.table ++ RVPrivileged.table ++ 
//    Trap.table
//
//  val table: Array[(BitPat, List[UInt])] = decodeArray.map(x => x.generate()).map{ case (pat, pats) => (pat, pats.map(bitPatToUInt)) }.toArray
//}