// package cpu.frontend
//
// import chisel3._
// import chisel3.util._
// import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
// import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
//
// import utility._
// import cpu._
//
// class IfuToPreDecode(parameter: CPUParameter) extends Bundle {
//   val data = Vec(parameter.PredictWidth, UInt(16.W))
//   val pc = UInt(parameter.VAddrBits.W)
// }
//
// class PreDecodeInfo(parameter: CPUParameter) extends Bundle {
//   val valid = Bool()
//   val brType = Brtype()
// }
//
// class PreDecodeResp(parameter: CPUParameter) extends Bundle {
//   val pd = Vec(parameter.PredictWidth, new PreDecodeInfo(parameter))
// }
//
// class PreDecodeInterface(parameter: CPUParameter) extends Bundle {
//   val clock = Input(Clock())
//   val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
//
//   val in = Input(new IfuToPreDecode(parameter))
//   val out = Output(new PreDecodeResp(parameter))
// }
//
// @instantiable
// class PreDecode(val parameter: CPUParameter)
//     extends FixedIORawModule(new PreDecodeInterface(parameter))
//     with SerializableModule[CPUParameter]
//     with ImplicitClock
//     with ImplicitReset
//     with HasPdConst {
//   override protected def implicitClock: Clock = io.clock
//   override protected def implicitReset: Reset = io.reset
//
//
// }
//
// trait HasPdConst {
//   // def C_JAL     = BitPat("b????????????????_?01_?_??_???_??_???_01") // RV32C
//   def C_J       = BitPat("b????????????????_101_?_??_???_??_???_01")
//   def C_EBREAK  = BitPat("b????????????????_100_?_00_000_00_000_10")
//   def C_JALR    = BitPat("b????????????????_100_?_??_???_00_000_10")  // c.jalr & c.jr
//   def C_BRANCH  = BitPat("b????????????????_11?_?_??_???_??_???_01")
//   def JAL       = BitPat("b????????????????_???_?????_1101111")
//   def JALR      = BitPat("b????????????????_000_?????_1100111")
//   def BRANCH    = BitPat("b????????????????_???_?????_1100011")
//   def NOP       = BitPat("b???????????????0_100_01010_0000001")   //li	a0,0
//
//   def isJal(inst: UInt): Bool = {inst === C_J || inst === JAL}
//   def isJalr(inst: UInt): Bool = {inst === C_JALR || inst === JALR}
//   def isBranch(inst: UInt): Bool = {inst === C_BRANCH || inst === BRANCH}
//
//   def isRVC(instr: UInt): Bool = (inst(1, 0) =/= 3.U)
//   def isLink(reg: UInt) = reg === 1.U || reg === 5.U
//
//   def rd(inst: UInt): UInt = {Mux(isRVC(inst), inst(12), inst(11,7))}
//   def rs(inst: UInt): UInt = {Mux(isRVC(inst), Mux(isJal(inst), 0.U, inst(11, 7)), inst(19, 15))}
//   def isCall(inst: UInt): Bool = {(isJal(inst) && !isRVC(inst) || isJalr(inst)) && isLink(rd(inst))} // Only for RV64
//   def isRet(inst: UInt): Bool = {isJalr(inst) && isLink(rs(inst)) && !isCall(inst)}
// }
//
