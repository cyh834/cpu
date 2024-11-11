package fifo

import chisel3._
import chisel3.util._

class FIFOIO[T <: Data](private val gen: T) extends Bundle {
  val wr_en = Input(Bool())
  val rd_en = Input(Bool())
  val data_in = Input(gen)
  val data_out = Output(gen)
  val full = Output(Bool())
  val empty = Output(Bool())
}

abstract class FIFO[T <: Data](gen: T, depth: Int) extends Module {
  val io = IO(new FIFOIO(gen))

  assert(depth > 0, "Number of buffer elements needs to be larger than 0")
}

class SyncFIFO[T <: Data](gen: T, depth: Int) extends FIFO(gen, depth) {

  val memReg = Reg(Vec(depth, gen)) // the register based memory

  val wr_ptr = Counter(depth) // fifo write pointer
  val rd_ptr = Counter(depth) // fifo read pointer

  val fullReg = RegInit(false.B) // fifo full flag
  val emptyReg = RegInit(true.B) // fifo empty flag

  // if write enable and fifo is not full, increment write pointer
  when(io.wr_en && !io.full) {
    memReg(wr_ptr.value) := io.data_in
    emptyReg := false.B
    fullReg := (wr_ptr.value + 1.U) === rd_ptr.value
    wr_ptr.inc()
  }

  // if read enable and fifo is not empty, increment read pointer
  when(io.rd_en && !io.empty) {
    fullReg := false.B
    emptyReg := (rd_ptr.value + 1.U) === wr_ptr.value
    rd_ptr.inc()
  }

  io.data_out := memReg(rd_ptr.value)
  io.full := fullReg
  io.empty := emptyReg

}
