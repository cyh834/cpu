package core.backend.fu

import chisel3._
import chisel3.util._
import chisel3.stage._
import core.HasCoreParameter

//class CSRIO extends FunctionUnitIO {
//  val cfIn = Flipped(new CtrlFlowIO)
//  val redirect = new RedirectIO
//  // for exception check
//  val instrValid = Input(Bool())
//  val isBackendException = Input(Bool())
//  // for differential testing
//  //val intrNO = Output(UInt(XLEN.W))
//  //val imemMMU = Flipped(new MMUIO)
//  //val dmemMMU = Flipped(new MMUIO)
//  //val wenFix = Output(Bool())
//}
//
//class CSR extends Module with HasCoreParameter{
//    val io = IO(new CSRIO)
//
//    val mstatus    = RegInit(UInt(XLEN.W), "ha00001800".U)
//    val mtvec      = RegInit(UInt(XLEN.W), 0.U)
//    val mepc       = RegInit(UInt(XLEN.W), 0.U)
//    val mcause     = RegInit(UInt(XLEN.W), 0.U)
//    val mie        = RegInit(UInt(XLEN.W), 0.U)
//    val mip        = RegInit(UInt(XLEN.W), 0.U)
//    val mvendorid  = RegInit(UInt(XLEN.W), 0.U)
//    val marchid    = RegInit(UInt(XLEN.W), 0.U)
//    val mhartid    = RegInit(UInt(XLEN.W), 0.U)
//
//    val mapping = Map(
//}