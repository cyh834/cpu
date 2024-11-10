package cpu.frontend

import chisel3._
import chisel3.util._

import utility._
import cpu._

object BPUParameter {
  implicit def rwP: upickle.default.ReadWriter[BPUParameter] =
    upickle.default.macroRW
}

case class BPUParameter(xlen: Int, VAddrBits: Int) extends SerializableModuleParameter{
  val NRbtb = 512
  val NRras = 16
  //多路?
  val waynum = 1

  val set = NRbtb/waynum
  val idxBits = log2Up(set)
  val wayBits = log2Up(waynum)
  val offsetBits = if(xlen == 32) 2 else 3
  // partial-tag?
  val tagBits = VAddrBits - idxBits - offsetBits
}

class BTBAddr(parameter: BPUParameter) extends Bundle{
  val tag = UInt(tagBits.W)
  val idx = UInt(idxBits.W)
  val way = UInt(wayBits.W)
  val offset = UInt(offsetBits.W)

  def fromUInt(x: UInt) = x.asTypeOf(UInt(VAddrBits.W)).asTypeOf(this)
  def getTag(x: UInt) = fromUInt(x).tag
  def getIdx(x: UInt) = fromUInt(x).idx
  def getway(x: UInt) = fromUInt(x).way
}

class BTBData(parameter: BPUParameter) extends Bundle{
  val valid = Bool()
  val BIA = UInt(tagBits.W)
  val BTA = UInt(VAddrBits.W)
  val brtype = Brtype()
}


//todo:加计数器统计相同地址
class RAS(nras: Int, vaddr: UInt){
  private val ras = Reg(Vec(nras, vaddr))
  private val sp = RegInit(0.U(log2Up(nras).W))

  def clear(): Unit = sp := 0.U
  def isEmpty: Bool = sp === 0.U

  def push(addr: UInt): Unit = {
    ras(sp) := addr
    sp := sp + 1.U
  }

  def pop(): Unit = {
    when(!isEmpty){
      sp := sp - 1.U
    }
  }

  def value: UInt = ras(sp)
}

//todo:竞争的分支预测+hash
//两位饱和计数器
class PHT(set: Int){
  private val pht = Mem(set, UInt(2.W))

  def value(addr: BTBAddr): UInt = pht.read(addr.idx)
  def taken(addr: BTBAddr): Bool = value(addr)(1)
  def update(addr: BTBAddr, realtaken: Bool): Unit = {
    val waddr = addr.idx
    val oldtaken = value(addr)
    val newtaken = Mux(realtaken, oldtaken + 1.U, oldtaken - 1.U)
    val wen = (realtaken && (oldtaken =/= "b11".U)) || (!realtaken && (oldtaken =/= "b00".U))

    when(wen){ pht.write(waddr, newtaken)}
  }
}

class BTB(set: Int){
  private val datatype = UInt((new BTBData).getWidth.W)
  private val btb = SyncReadMem(set, datatype)

  def read(addr: BTBAddr): BTBData = {
    btb.read(addr.idx).asTypeOf(new BTBData)
  }

  def hit(addr: BTBAddr): Bool = {
    val data = read(addr)
    data.valid && data.BIA === addr.tag
  }

  def update(addr: BTBAddr, wdata: BTBData): Unit = {
    btb.write(addr.idx, wdata.asTypeOf(datatype))
  }

}

class BPUInterface(parameter: BPUParameter) extends Bundle{
  val in = Flipped(Valid(new BPUReq(parameter)))
  val flush = Input(Bool())
  val out = Valid(new PredictIO(parameter))
  val btb_update = Flipped(Valid(new BTBUpdate(parameter)))
  val pht_update = Flipped(Valid(new PHTUpdate(parameter)))
  val ras_update = Flipped(Valid(new RASUpdate(parameter)))
}

class BPU(val parameter: BPUParameter)
   extends FixedIORawModule(new BPUInterface(parameter))
   with SerializableModule[BPUParameter]{
  val pc = io.in.bits.pc
  implicit def fromUInt(pc: UInt): BTBAddr = (new BTBAddr(parameter)).fromUInt(pc)

  // BTB
  val btb = new BTB(set)
  val btbRead = btb.read(pc)
  val btbData = Wire(new BTBData(parameter))
  btbData.valid := true.B
  btbData.BIA   := io.btb_update.bits.pc.tag
  btbData.BTA   := io.btb_update.bits.target
  btbData.brtype := io.btb_update.bits.brtype

  when(io.btb_update.valid){
    btb.update(io.btb_update.bits.pc, btbData)
  }

  // PHT
  val pht = new PHT(set)
  val pred_taken = pht.taken(pc)
  when (io.pht_update.valid) {
    pht.update(pc, io.pht_update.bits.taken)
  }

  // RAS
  val ras = new RAS(NRras, UInt(VAddrBits.W))
  val rasTarget = ras.value
  when (io.ras_update.valid) {
    when (io.ras_update.bits.brtype === Brtype.call) {
      ras.push(Mux(io.ras_update.bits.isRVC, pc + 2.U, pc + 4.U))
    }
    .elsewhen (io.ras_update.bits.brtype === Brtype.ret) {
      ras.pop()
    }
  }

  //跳转目标
  val target = Mux(btbRead.brtype === Brtype.ret, rasTarget, btbRead.BTA)
  //是否跳转
  val brIdx = btb.hit(pc) && Mux(btbRead.brtype === Brtype.branch, pred_taken, true.B)

  // update pc
  io.out.bits.target := target
  io.out.bits.pred_taken := brIdx
  io.out.valid := true.B
}