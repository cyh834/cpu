//package core.frontend
//
//import chisel3._
//import chisel3.util._
//import chisel3.util.experimental.BoringUtils
//import core.HasCoreParameter
//import top.Settings
//import core.cache._
//import utility._
//
//class BPU2FTQIO extends Bundle with HasBPUParameter{
//  val pc = Input(UInt(VAddrBits.W))
//  val miss = Input(Bool())
//  val real_pc = Input(UInt(VAddrBits.W))
//  val actualTaken = Input(Bool())
//  val ptype = Input(Ptype())
//  val br_type = Input(Brtype())
//  val isRVC = Input(Bool())
//  val nofull = Input(Bool())
//  val leftvalid = Output(Bool())
//  val bp_pc = Output(UInt(VAddrBits.W))
//}
//
//class Ftq2IFUIO extends Bundle with HasCoreParameter{
//  val fetch_req = Output(Bool())
//  val redict = Input(Bool())
//  val pc = Output(UInt(VAddrBits.W))
//}
//
//class Ftq2IcacheIO extends InsUncacheReq
//
//class Ftq2Backend extends Bundle with HasCoreParameter{
//  val pc = Output(UInt(VAddrBits.W))
//  val exu_res = Input(Bool())
//  val commit = Input(Bool())
//}
//
//trait HasFTQParameter extends HasCoreParameter{
//  val ftqSize = 8
//}
//
//class jmpInfo extends Bundle with HasFTQParameter{
//  val valid = Bool()
//  val bits = UInt(3.W)
//
//  def hasJal = valid && !bits(0)
//  def hasJalr = valid && bits(0)
//  def hasCall = valid && bits(1)
//  def hasRet = valid && bits(2)
//}
//
//class PD_MEM extends Bundle with HasFTQParameter{
//  val brMask = Bool()
//  val jmpInfo = new jmpInfo
//  val jmpOffset = UInt(VAddrBits.W)
//  val jalTarget = UInt(VAddrBits.W)
//  val rvcMask = Bool()
//}
//
//class FTQ extends Module with HasFTQParameter{
//  val io = IO(new Bundle {
//    val bpu = new Ftq2BPUIO
//    val icache = DecoupledIO(new Ftq2IcacheIO)
//    val backend = new Ftq2Backend
//    val ifu = new Ftq2IFUIO
//  })
//  val ftq_pc_mem = RegInit(VecInit(Seq.fill(ftqSize)(0.U(VAddrBits.W))))
//  val ftq_pd_mem = RegInit(VecInit(Seq.fill(ftqSize)(0.U.asTypeOf(new PD_MEM))))
//
//  val widx = RegInit(0.U(log2Up(ftqSize).W))
//  def pc_write(wen:Bool, wdata:UInt) = {
//    ftq_pc_mem(widx) := Mux(wen, wdata, ftq_pc_mem(widx))
//    widx := widx + wen
//  }
//  pc_write(io.bpu.leftvalid, io.bpu.bp_pc)
//
//  val ridx = RegInit(0.U(log2Up(ftqSize).W))
//  def pc_read(ren:Bool): UInt = {
//    ftq_pc_mem(ridx)
//  }
//
//  when(io.icache.fire){
//    ridx := ridx + 1.U
//  }
//
//  val pc = pc_read(io.icache.valid)
//  io.icache.bits.addr := pc
//  io.icache.valid := widx =/= ridx
//
//  io.ifu.pc := RegNext(pc, init = 0.U)
//}
