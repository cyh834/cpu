package cpu

import chisel3._
import chisel3.util._
import cpu.frontend.BPUParameter

class ForwardIO(LogicRegsWidth: Int, XLEN: Int) extends Bundle {
  val rfDest = Output(UInt(LogicRegsWidth.W))
  val rfData = Output(UInt(XLEN.W))
  val valid = Output(Bool())
}

class RedirectIO(VAddrBits: Int) extends Bundle {
  val target = Output(UInt(VAddrBits.W))
  val valid = Output(Bool())
  // val realtaken = Output(Bool())
}

/** IFU */
class IFU2IBUF(VAddrBits: Int) extends Bundle {
  val pc = UInt(VAddrBits.W)
  val inst = UInt(64.W)
  val brIdx = UInt(4.W)
  val instValid = UInt(4.W)
}

/** IDU */
