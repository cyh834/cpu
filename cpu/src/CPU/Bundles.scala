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

/** BPU */
object Brtype {
  def branch = 0.U
  def jump = 1.U
  def call = 2.U
  def ret = 3.U
  def X = 0.U

  def apply() = UInt(2.W)

  def isRas(x: UInt): Bool = x === call || x === ret
}

class PredictIO(vaddrBits: Int) extends Bundle {
  val pc = Output(UInt(vaddrBits.W))
  val pred_taken = Output(Bool())
}

class IFUReq(dataBits: Int, vaddrBits: Int) extends PredictIO(vaddrBits) {
  val data = Input(UInt(dataBits.W))
}

//IFU
class RASUpdate(parameter: BPUParameter) extends Bundle {
  val brtype = Brtype()
  val isRVC = Bool()
}

//EXU
class PHTUpdate(parameter: BPUParameter) extends Bundle {
  val pc = UInt(parameter.vaddrBits.W)
  val taken = Bool()
}

//EXU?
class BTBUpdate(parameter: BPUParameter) extends Bundle {
  val pc = UInt(parameter.vaddrBits.W)
  val target = UInt(parameter.vaddrBits.W)
  // val taken = Bool()
  // val isValid = Bool()
  val brtype = Brtype()
}

/** IFU */
class IFU2IBUF(VAddrBits: Int) extends Bundle {
  val pc = UInt(VAddrBits.W)
  val inst = UInt(32.W)
  val pred_taken = Bool()
  val isRVC = Bool()
}

/** IBUF */
class IBUF2IDU(VAddrBits: Int) extends Bundle {
  val pc = UInt(VAddrBits.W)
  val inst = UInt(32.W)
  val pred_taken = Bool()
  val isRVC = Bool()
//  val exceptionVec    = ExceptionVec()
//  val preDecodeInfo   = new PreDecodeInfo
//  val crossPageIPFFix = Bool()
}

/** IDU */
