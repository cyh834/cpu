package fifo

import chisel3._
import chisel3.util._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}

class FIFOIO[T <: Data](
  private val gen:   T,
  val depth:         Int,
  val maxReadNum:    Int,
  val maxWriteNum:   Int,
  val useAsyncReset: Boolean)
    extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (useAsyncReset) AsyncReset() else Bool())
  val flush = Input(Bool())

  val wr_en = Input(Bool())
  val wr_num = Input(UInt(maxWriteNum.W)) // 一次性写入的数量
  val data_in = Input(Vec(maxWriteNum, gen))

  val rd_en = Input(Bool())
  val rd_num = Input(UInt(maxReadNum.W)) // 一次性读取的数量
  val data_out = Output(Vec(maxReadNum, gen))

  // 剩余写入空间
  val remaining_write_space = Output(UInt(log2Up(depth).W))
  // 剩余读取空间
  val remaining_read_space = Output(UInt(log2Up(depth).W))
}

abstract class FIFO[T <: Data](gen: T, depth: Int, maxReadNum: Int, maxWriteNum: Int, useAsyncReset: Boolean = false)
    extends FixedIORawModule(new FIFOIO(gen, depth, maxReadNum, maxWriteNum, useAsyncReset))
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  assert(depth > 0, "Number of buffer elements needs to be larger than 0")
  assert((depth & (depth - 1)) == 0, "Depth must be a power of 2")
  assert(maxReadNum > 0, "Number of read elements needs to be larger than 0")
  assert(maxReadNum <= depth, "Number of read elements needs to be less than or equal to the depth")
  assert(maxWriteNum > 0, "Number of write elements needs to be larger than 0")
  assert(maxWriteNum <= depth, "Number of write elements needs to be less than or equal to the depth")
}

@instantiable
class SyncFIFO[T <: Data](gen: T, depth: Int, maxReadNum: Int, maxWriteNum: Int, useAsyncReset: Boolean)
    extends FIFO(gen, depth, maxReadNum, maxWriteNum, useAsyncReset) {

  val memReg = Reg(Vec(depth, gen)) // the register based memory

  val wr_ptr = RegInit(0.U(log2Up(depth).W)) // fifo write pointer
  val rd_ptr = RegInit(0.U(log2Up(depth).W)) // fifo read pointer
  val fifo_counter = RegInit(0.U(log2Up(depth + 1).W)) // fifo counter

  // Calculate remaining space
  val rdrs = fifo_counter
  val wrrs = depth.U - fifo_counter

  // if write enable and fifo has enough space, increment write pointer
  when(io.wr_en && wrrs >= io.wr_num) {
    for (i <- 0 until maxWriteNum) {
      memReg(wr_ptr + i.U) := io.data_in(i.U)
    }
    wr_ptr := wr_ptr + io.wr_num
  }

  io.data_out := 0.U.asTypeOf(Vec(maxReadNum, gen)) // default value
  // if read enable and fifo has enough data, increment read pointer
  when(io.rd_en && rdrs >= io.rd_num) {
    for (i <- 0 until maxReadNum) {
      io.data_out(i.U) := memReg(rd_ptr + i.U)
    }
    rd_ptr := rd_ptr + io.rd_num
  }

  fifo_counter := fifo_counter + Mux(io.wr_en && wrrs >= io.wr_num, io.wr_num, 0.U) - Mux(
    io.rd_en && rdrs >= io.rd_num,
    io.rd_num,
    0.U
  )

  io.remaining_write_space := wrrs
  io.remaining_read_space := rdrs

  when(io.flush) {
    wr_ptr := 0.U
    rd_ptr := 0.U
    fifo_counter := 0.U
    memReg.foreach(_ := 0.U.asTypeOf(gen))
  }
}
