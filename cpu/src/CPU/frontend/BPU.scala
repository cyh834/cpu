package cpu.frontend

import chisel3._
import chisel3.util._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}

import utility._
import cpu._

object BPUParameter {
  implicit def rwP: upickle.default.ReadWriter[BPUParameter] =
    upickle.default.macroRW
}

case class BPUParameter(NRbtb: Int, NRras: Int, fetchWidth: Int, useCompressed: Boolean, useAsyncReset: Boolean, vaddrBits: Int)
    extends SerializableModuleParameter {
  val coreInstBytes = (if (useCompressed) 16 else 32) / 8
  val waynum = fetchWidth / coreInstBytes  // For RV64IMAC, 8/2=4
  val set = NRbtb / waynum  // 512/4=128
  val idxBits = log2Up(set) // log2(128)=7  
  val wayBits = log2Up(waynum) // log2(4)=2
  val coreBits = log2Up(coreInstBytes) // log2(2)=1

  // partial-tag?
  val tagBits = vaddrBits - idxBits - coreBits
}

class BTBAddr(parameter: BPUParameter) extends Bundle {
  val tag = UInt(parameter.tagBits.W)
  val idx = UInt(parameter.idxBits.W) 
  val way = UInt(parameter.wayBits.W) // pc[2:1]
  val core = UInt(parameter.coreBits.W) // pc[0]

  def fromUInt(x: UInt) = x.asTypeOf(UInt(parameter.vaddrBits.W)).asTypeOf(this)
  def getTag(x:   UInt) = fromUInt(x).tag
  def getIdx(x:   UInt) = fromUInt(x).idx
  def getWay(x:   UInt) = fromUInt(x).way
  def getCore(x:   UInt) = fromUInt(x).core
}

object Brtype {
  def branch = 0.U
  def jump = 1.U
  def call = 2.U
  def ret = 3.U

  def isRas(x: UInt): Bool = x === call || x === ret
  def apply() = UInt(2.W)
}

class BTBData(parameter: BPUParameter) extends Bundle {
  val BIA = UInt(parameter.tagBits.W)
  val BTA = UInt(parameter.vaddrBits.W)
  val brtype = Brtype()
  val crosslineJump = Bool()
  val valid = Bool()
}

class BTB(parameter: BPUParameter) {
  private val waynum = parameter.waynum
  private val set = parameter.set

  private val datatype = UInt((new BTBData(parameter)).getWidth.W)
  private val btb = Seq.tabulate(waynum) { _ => SyncReadMem(set, datatype) }

  def read(addr: BTBAddr): Vec[BTBData] = {
    VecInit((0 to (waynum-1)).map(i => btb(i).read(addr.idx).asTypeOf(new BTBData(parameter))))
  }

  def hit(addr: BTBAddr, data: Vec[BTBData]): Vec[Bool] = {
    VecInit((0 to (waynum-1)).map(i => data(i).valid && data(i).BIA === addr.tag))
  }

  def update(addr: BTBAddr, wdata: BTBData): Unit = {
    val writeEnables = (0 until waynum).map(i => addr.way === i.U)
    writeEnables.zip(btb).foreach { case (wen, mem) =>
      when(wen) {
        mem.write(addr.idx, wdata.asTypeOf(datatype))
      }
    }
  }
}

//todo:竞争的分支预测+hash
//两位饱和计数器
class PHT(parameter: BPUParameter) {
  private val waynum = parameter.waynum
  private val set = parameter.set

  private val pht = Seq.tabulate(waynum)(_ => Mem(set, UInt(2.W)))

  def value(addr: BTBAddr): Vec[UInt] = VecInit((0 to (waynum-1)).map(i => pht(i).read(addr.idx)))
  def taken(addr: BTBAddr): Vec[Bool] = VecInit((0 to (waynum-1)).map(i => value(addr)(i)(1)))
  def update(addr: BTBAddr, realtaken: Bool): Unit = {
    val oldtaken = value(addr)(addr.way)
    val newtaken = Mux(realtaken, oldtaken + 1.U, oldtaken - 1.U)
    val wen = (realtaken && (oldtaken =/= "b11".U)) || (!realtaken && (oldtaken =/= "b00".U))
    val writeEnables = (0 until waynum).map(i => addr.way === i.U)
    writeEnables.zip(pht).foreach { case (wen, mem) =>
      when(wen) {
        mem.write(addr.idx, newtaken)
      }
    }
  }
}

//todo:加计数器统计相同地址
class RAS(parameter: BPUParameter) {
  val nras = parameter.NRras
  val vaddr = UInt(parameter.vaddrBits.W)

  private val ras = Reg(Vec(nras, vaddr))
  private val sp = RegInit(0.U(log2Up(nras).W))

  def clear(): Unit = sp := 0.U
  def isEmpty: Bool = sp === 0.U

  def push(addr: UInt): Unit = {
    ras(sp) := addr
    sp := sp + 1.U
  }

  def pop(): Unit = {
    when(!isEmpty) {
      sp := sp - 1.U
    }
  }

  def value: UInt = ras(sp)
}

class BPUReq(parameter: BPUParameter) extends Bundle {
  val pc = UInt(parameter.vaddrBits.W)
}

class PredictIO(parameter: BPUParameter) extends Bundle {
  val pc = Output(UInt(parameter.vaddrBits.W))
  val brIdx = Output(Vec(parameter.waynum, Bool()))
  val crosslineJump = Output(Bool())
  // val jump = Output(Bool())
}

class BTBUpdate(parameter: BPUParameter) extends Bundle {
  val pc = UInt(parameter.vaddrBits.W)
  val target = UInt(parameter.vaddrBits.W)
  val brtype = Brtype()
  val isRVC = Bool()
}

class PHTUpdate(parameter: BPUParameter) extends Bundle {
  val pc = UInt(parameter.vaddrBits.W)
  val taken = Bool()
}

class RASUpdate(parameter: BPUParameter) extends Bundle {
  val brtype = Brtype()
  val isRVC = Bool()
}

class BPUUpdate(parameter: BPUParameter) extends Bundle {
  val btb = Valid(new BTBUpdate(parameter))
  val pht = Valid(new PHTUpdate(parameter))
  val ras = Valid(new RASUpdate(parameter))
}

class BPUInterface(parameter: BPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val flush = Input(Bool())
  val in = Flipped(Valid(new BPUReq(parameter)))
  val out = Valid(new PredictIO(parameter))
  val update = Input(Flipped(new BPUUpdate(parameter)))
}

@instantiable
class BPU(val parameter: BPUParameter)
    extends FixedIORawModule(new BPUInterface(parameter))
    with SerializableModule[BPUParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset
  implicit def fromUInt(pc: UInt): BTBAddr = (new BTBAddr(parameter)).fromUInt(pc)

  val pc = io.in.bits.pc
  val pcLatch = RegEnable(pc, io.in.valid)
  val waynum = parameter.waynum

  // BTB
  val btb = new BTB(parameter)
  val btbRead = btb.read(pc)
  val btbHit = btb.hit(pcLatch, btbRead) // btb的数据要延迟一个周期才能读出

  // 更新BTB
  val btbData = Wire(new BTBData(parameter))
  btbData.BIA := io.update.btb.bits.pc.tag
  btbData.BTA := io.update.btb.bits.target
  btbData.brtype := io.update.btb.bits.brtype
  btbData.crosslineJump := (io.update.btb.bits.pc.way === (parameter.waynum - 1).U) && !io.update.btb.bits.isRVC
  btbData.valid := true.B
  when(io.update.btb.valid) {
    btb.update(io.update.btb.bits.pc, btbData)
  }

  // PHT
  val pht = new PHT(parameter)
  val phtTaken = RegEnable(pht.taken(pc), io.in.valid)

  // 更新PHT
  when(io.update.pht.valid) {
    pht.update(pc, io.update.pht.bits.taken)
  }

  // RAS
  val ras = new RAS(parameter)
  val rasTarget = RegEnable(ras.value, io.in.valid)

  // 更新RAS
  when(io.update.ras.valid) {
    when(io.update.ras.bits.brtype === Brtype.call) {
      ras.push(Mux(io.update.ras.bits.isRVC, pc + 2.U, pc + 4.U))
    }
    .elsewhen(io.update.ras.bits.brtype === Brtype.ret) {
      ras.pop()
    }
  }

  // 输出
  def genInstValid(pc: BTBAddr) = (Fill(waynum, 1.U(1.W)) << pc.way)(waynum-1, 0)
  val pcLatchValid = genInstValid(pcLatch)
  val target = Wire(Vec(waynum, UInt(parameter.vaddrBits.W)))
  (0 to (waynum-1)).map(i => target(i) := Mux(btbRead(i).brtype === Brtype.ret, rasTarget, btbRead(i).BTA))
  (0 to (waynum-1)).map(i => io.out.bits.brIdx(i) := btbHit(i) && pcLatchValid(i).asBool && Mux(btbRead(i).brtype === Brtype.branch, phtTaken(i), true.B))

  io.out.bits.crosslineJump := btbRead(waynum - 1).crosslineJump && btbHit(waynum - 1) &&  (0 to (waynum - 2)).map(i => !io.out.bits.brIdx(i)).reduce(_ && _)
  io.out.bits.pc := PriorityMux(io.out.bits.brIdx, target)
  io.out.valid := RegNext(io.in.valid) && io.out.bits.brIdx.asUInt.orR
}
