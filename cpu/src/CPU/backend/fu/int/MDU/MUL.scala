package cpu.backend.fu

import chisel3._
import chisel3.util._
import chisel3.stage._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import cpu._
import utility._

// TODO: 更优雅的实现&流水化
class CSAInterface extends Bundle {
  val in = Input(UInt(3.W))
  val out = Output(UInt(2.W))
}

@instantiable
class CSA extends FixedIORawModule(new CSAInterface){
  val out = Wire(Vec(2, Bool()))
  out(1) := (io.in(0) & io.in(1)) | (io.in(2) & (io.in(0) ^ io.in(1))) // cout
  out(0) := io.in(0) ^ io.in(1) ^ io.in(2) // sum
  io.out := Cat(out(1), out(0))
}

class switchInterface extends Bundle {
  val in = Input(Vec(33, UInt(132.W)))
  val cin = Input(Vec(33, UInt(1.W)))
  val out = Output(Vec(132, UInt(33.W)))
  val cout = Output(UInt(32.W))
}

@instantiable
class switch extends FixedIORawModule(new switchInterface){
  for (j <- 0 until 132) {
    val c = Wire(Vec(33, UInt(1.W)))
    for (i <- 0 until 33) {
      c(i) := io.in(i)(j)
    }
    io.out(j) := c.asUInt
  }
  io.cout := io.cin.asUInt(31, 0)

  //for(j <- 0 until 132)
  //  for(i <- 0 until 33){
  //    chisel3.assert(io.out(j)(i) === io.in(i)(j))
  //  }
}

class gen_p_Interface extends Bundle {
  val src = Input(UInt(3.W))
  val x = Input(UInt(132.W))
  val p = Output(UInt(132.W))
  val c = Output(UInt(1.W))
}

@instantiable
class gen_p extends FixedIORawModule(new gen_p_Interface){
  def gen_p_i(x: Bool, x_sub: Bool, sel: UInt): Bool = {
    val sel_negative = sel(0)
    val sel_positive = sel(1)
    val sel_double_negative = sel(2)
    val sel_double_positive = sel(3)
  
    ~(~(sel_negative & ~x) & ~(sel_double_negative & ~x_sub)
      & ~(sel_positive & x) & ~(sel_double_positive & x_sub))
  }
  
  def gen_sel(src: UInt): UInt = {
    val y_sub = src(0)
    val y = src(1)
    val y_add = src(2)
  
    Cat(
      (y_add & (y & ~y_sub | ~y & y_sub)).asUInt,   // sel_double_positive
      (~y_add & (y & ~y_sub | ~y & y_sub)).asUInt,  // sel_double_negative
      (y_add & ~y & ~y_sub).asUInt,                 // sel_positive
      (~y_add & y & y_sub).asUInt                   // sel_negative
    )
  }
  val sel = gen_sel(io.src)
  val p0 = gen_p_i(io.x(0), false.B, sel)
  val pi = (1 until 132).map(i => gen_p_i(io.x(i), io.x(i - 1), sel))
  val p = Cat(Cat(pi.reverse), p0.asUInt)
  io.p := p
  io.c := sel(0) | sel(2) // -x or -2x
}

class WallocInterface extends Bundle {
  val src_in = Input(UInt(33.W))
  val cin = Input(UInt(30.W))
  val cout_group = Output(Vec(30, UInt(1.W)))
  val cout = Output(UInt(1.W))
  val s = Output(UInt(1.W))
}
//1 3 7 9 11
@instantiable
class Walloc33bits extends FixedIORawModule(new WallocInterface){
  val cin = io.cin
  val csa = Seq.fill(31)(Instantiate(new CSA))
  val c = Wire(Vec(30, UInt(1.W)))
  // First
  val first_s = Wire(Vec(11, UInt(1.W)))
  for (i <- 0 until 11) {
    csa(i).io.in := io.src_in(32 - i * 3, 30 - i * 3)
    c(10 - i) := csa(i).io.out(1)
    first_s(10 - i) := csa(i).io.out(0)
  }

  // Second
  val second_s = Wire(Vec(9, UInt(1.W)))
  csa(11).io.in := first_s.asUInt(10, 8)
  csa(12).io.in := first_s.asUInt(7, 5)
  csa(13).io.in := first_s.asUInt(4, 2)
  csa(14).io.in := Cat(first_s.asUInt(1, 0), cin(15))
  csa(15).io.in := cin(14, 12)
  csa(16).io.in := cin(11, 9)
  csa(17).io.in := cin(8, 6)
  csa(18).io.in := cin(5, 3)
  csa(19).io.in := cin(2, 0)

  for (i <- 0 until 9) {
    c(19 - i) := csa(i + 11).io.out(1)
    second_s(8 - i) := csa(i + 11).io.out(0)
  }

  // Third
  val third_s = Wire(Vec(7, UInt(1.W)))
  csa(20).io.in := second_s.asUInt(8, 6)
  csa(21).io.in := second_s.asUInt(5, 3)
  csa(22).io.in := second_s.asUInt(2, 0)
  csa(23).io.in := cin(27, 25)
  csa(24).io.in := cin(24, 22)
  csa(25).io.in := cin(21, 19)
  csa(26).io.in := cin(18, 16)

  for (i <- 0 until 7) {
    c(26 - i) := csa(i + 20).io.out(1)
    third_s(6 - i) := csa(i + 20).io.out(0)
  }

  // Fourth
  val fourth_s = Wire(Vec(3, UInt(1.W)))
  csa(27).io.in := third_s.asUInt(6, 4)
  csa(28).io.in := third_s.asUInt(3, 1)
  csa(29).io.in := Cat(third_s.asUInt(0), cin(29, 28))

  for (i <- 0 until 3) {
    c(29 - i) := csa(i + 27).io.out(1)
    fourth_s(2 - i) := csa(i + 27).io.out(0)
  }

  // Fifth
  csa(30).io.in := fourth_s.asUInt(2, 0)
  io.cout := csa(30).io.out(1)
  io.s := csa(30).io.out(0)

  io.cout_group := c

}

class walloc_64_mul_interface extends Bundle {
  val multiplicand = Input(UInt(132.W))
  val multiplier = Input(UInt(66.W))
  val result = Output(UInt(128.W))
}

@instantiable
class walloc_64_mul extends FixedIORawModule(new walloc_64_mul_interface){
  val mula = io.multiplicand // RegEnable(io.multiplicand,0.U(132.W),io.in.valid.asBool)
  val mulb = Cat(io.multiplier, 0.U) // RegEnable(Cat(io.multiplier,0.U),0.U(67.W),io.in.valid.asBool)
  
  val gp = Seq.fill(33)(Instantiate(new gen_p).io)
  val sw = Instantiate(new switch).io
  val wa = Seq.fill(132)(Instantiate(new Walloc33bits).io)
  val adder_a = Wire(Vec(132, UInt(1.W)))
  val adder_b = Wire(Vec(133, UInt(1.W)))

  for (i <- 0 until 33) {
    gp(i).src := mulb(2 + 2 * i, 2 * i)
    gp(i).x := mula << (i * 2).U
    sw.in(i) := gp(i).p
    sw.cin(i) := gp(i).c
  }

  wa(0).src_in := sw.out(0)
  wa(0).cin := sw.cout(31, 2)
  adder_a(0) := wa(0).s
  adder_b(1) := wa(0).cout
  for (i <- 1 until 132) {
    wa(i).src_in := sw.out(i)
    wa(i).cin := wa(i - 1).cout_group.asUInt
    adder_a(i) := wa(i).s
    adder_b(i + 1) := wa(i).cout
  }

  adder_b(0) := sw.cout(1)
  val src1 = dontTouch(Wire(UInt(133.W)))
  val src2 = dontTouch(Wire(UInt(133.W)))
  val cin = dontTouch(Wire(UInt(133.W)))
  src1 := adder_a.asUInt
  src2 := adder_b.asUInt
  cin := sw.cout(0)

  io.result := (src1 + src2 + cin)(127, 0)
}

class MULInterface(parameter: CPUParameter) extends Bundle {
  val XLEN = parameter.XLEN
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())

  val flush = Input(Bool())
  val in = Flipped(Decoupled(Vec(2, Input(UInt((XLEN+1).W)))))
  val out = ValidIO(Output(UInt((XLEN*2).W)))
}

@instantiable
class MUL (val parameter: CPUParameter)
    extends FixedIORawModule(new MULInterface(parameter))
    with SerializableModule[CPUParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val a = RegEnable(io.in.bits(0), io.in.fire)
  val b = RegEnable(io.in.bits(1), io.in.fire)
  val busy = RegInit(false.B)
  when(io.flush.asBool){
    busy := false.B
  }.elsewhen(io.out.valid){
    busy := false.B
  }.elsewhen(io.in.fire){
    busy := true.B
  }

  // val tree = Instantiate(new walloc_64_mul).io
  // tree.multiplicand := SignExt(a, 132)
  // tree.multiplier := SignExt(b, 66)

  io.out.bits := RegNext(a * b) //RegNext(tree.result)
  io.out.valid := RegNext(RegNext(io.in.fire))
  io.in.ready := !busy

}

