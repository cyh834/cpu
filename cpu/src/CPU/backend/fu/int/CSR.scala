package cpu.backend.fu

import chisel3._
import chisel3.util._
import chisel3.stage._
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import cpu._
import utility._

trait HasCSRConst {
  // User Trap Setup
  //val Ustatus       = 0x000
  //val Uie           = 0x004
  //val Utvec         = 0x005

  // User Trap Handling
  //val Uscratch      = 0x040
  val Uepc          = 0x041
  //val Ucause        = 0x042
  //val Utval         = 0x043
  //val Uip           = 0x044

  // Supervisor Trap Setup
  val Sstatus       = 0x100
  val Sedeleg       = 0x102
  val Sideleg       = 0x103
  val Sie           = 0x104
  val Stvec         = 0x105
  val Scounteren    = 0x106

  // Supervisor Trap Handling
  val Sscratch      = 0x140
  val Sepc          = 0x141
  val Scause        = 0x142
  val Stval         = 0x143
  val Sip           = 0x144

  // Supervisor Protection and Translation
  val Satp          = 0x180

  // Machine Information Registers
  val Mvendorid     = 0xF11
  val Marchid       = 0xF12
  val Mimpid        = 0xF13
  val Mhartid       = 0xF14

  // Machine Trap Setup
  val Mstatus       = 0x300
  val Misa          = 0x301
  val Medeleg       = 0x302
  val Mideleg       = 0x303
  val Mie           = 0x304
  val Mtvec         = 0x305
  val Mcounteren    = 0x306

  // Machine Trap Handling
  val Mscratch      = 0x340
  val Mepc          = 0x341
  val Mcause        = 0x342
  val Mtval         = 0x343
  val Mip           = 0x344

  // Machine Memory Protection
  // TBD
  val Pmpcfg0       = 0x3A0
  val Pmpcfg1       = 0x3A1
  val Pmpcfg2       = 0x3A2
  val Pmpcfg3       = 0x3A3
  val PmpaddrBase   = 0x3B0


  def privEcall  = 0x000.U
  def privEbreak = 0x001.U
  def privMret   = 0x302.U
  def privSret   = 0x102.U
  def privUret   = 0x002.U

  def ModeM     = 0x3.U
  def ModeH     = 0x2.U
  def ModeS     = 0x1.U
  def ModeU     = 0x0.U

  def IRQ_UEIP  = 0
  def IRQ_SEIP  = 1
  def IRQ_MEIP  = 3

  def IRQ_UTIP  = 4
  def IRQ_STIP  = 5
  def IRQ_MTIP  = 7

  def IRQ_USIP  = 8
  def IRQ_SSIP  = 9
  def IRQ_MSIP  = 11

  val IntPriority = Seq(
    IRQ_MEIP, IRQ_MSIP, IRQ_MTIP,
    IRQ_SEIP, IRQ_SSIP, IRQ_STIP,
    IRQ_UEIP, IRQ_USIP, IRQ_UTIP
  )
}

trait HasExceptionNO {
  def instrAddrMisaligned = 0
  def instrAccessFault    = 1
  def illegalInstr        = 2
  def breakPoint          = 3
  def loadAddrMisaligned  = 4
  def loadAccessFault     = 5
  def storeAddrMisaligned = 6
  def storeAccessFault    = 7
  def ecallU              = 8
  def ecallS              = 9
  def ecallM              = 11
  def instrPageFault      = 12
  def loadPageFault       = 13
  def storePageFault      = 15

  val ExcPriority = Seq(
      breakPoint, // TODO: different BP has different priority
      instrPageFault,
      instrAccessFault,
      illegalInstr,
      instrAddrMisaligned,
      ecallM, ecallS, ecallU,
      storeAddrMisaligned,
      loadAddrMisaligned,
      storePageFault,
      loadPageFault,
      storeAccessFault,
      loadAccessFault
  )
}

class CSRProbe(parameter: CPUParameter) extends Bundle {
  val csr: Vec[UInt] = Vec(18, UInt(64.W))
}

class CSRInterface(parameter: CPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val valid = Input(Bool())
  val src = Vec(2, Input(UInt(parameter.XLEN.W)))
  val imm = Input(UInt(parameter.XLEN.W))
  val pc = Input(UInt(parameter.XLEN.W))
  val func = Input(FuOpType())
  val zimm = Input(UInt(5.W))
  val result = Output(UInt(parameter.XLEN.W))
  val redirect = new RedirectIO(parameter.VAddrBits)
  val probe = Output(Probe(new CSRProbe(parameter), layers.Verification))

  //val redirect = new RedirectIO
  // for exception check
  //val instrValid = Input(Bool())
  //val isBackendException = Input(Bool())
  //for differential testing
  //val intrNO = Output(UInt(XLEN.W))
  //val imemMMU = Flipped(new MMUIO)
  //val dmemMMU = Flipped(new MMUIO)
  //val wenFix = Output(Bool())
}

class CSR(val parameter: CPUParameter)
    extends FixedIORawModule(new CSRInterface(parameter))
    with SerializableModule[CPUParameter]
    with ImplicitClock
    with ImplicitReset
    with HasCSRConst
    with HasExceptionNO {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset
  val XLEN = parameter.XLEN
  val VAddrBits = parameter.VAddrBits
  val AddrBits = XLEN

  class Priv extends Bundle {
    val m = Output(Bool())
    val h = Output(Bool())
    val s = Output(Bool())
    val u = Output(Bool())
  }
  class StatusStruct extends Bundle {
    val sd = Output(UInt(1.W))

    val pad1 = if (XLEN == 64) Output(UInt(27.W)) else null
    val sxl  = if (XLEN == 64) Output(UInt(2.W))  else null
    val uxl  = if (XLEN == 64) Output(UInt(2.W))  else null
    val pad0 = if (XLEN == 64) Output(UInt(9.W))  else Output(UInt(8.W))

    val tsr = Output(UInt(1.W))
    val tw = Output(UInt(1.W))
    val tvm = Output(UInt(1.W))
    val mxr = Output(UInt(1.W))
    val sum = Output(UInt(1.W))
    val mprv = Output(UInt(1.W))
    val xs = Output(UInt(2.W))
    val fs = Output(UInt(2.W))
    val mpp = Output(UInt(2.W))
    val hpp = Output(UInt(2.W))
    val spp = Output(UInt(1.W))
    val pie = new Priv
    val ie = new Priv
  }

  class SatpStruct extends Bundle {
    val mode = UInt(4.W)
    val asid = UInt(16.W)
    val ppn  = UInt(44.W)
  }

  class Interrupt extends Bundle {
    val e = new Priv
    val t = new Priv
    val s = new Priv
  }

  // Machine-Level CSRs
  val mvendorid = RegInit(UInt(XLEN.W), 0.U)
  val marchid = RegInit(UInt(XLEN.W), 0.U)
  val mimpid = RegInit(UInt(XLEN.W), 0.U)
  val mhartid = RegInit(UInt(XLEN.W), 0.U)

  val mstatus = RegInit(UInt(XLEN.W), "ha00000000".U)
  val misa = RegInit(UInt(XLEN.W), "h8000000000141105".U) // RV64IMAC, MSU
  val medeleg = RegInit(UInt(XLEN.W), 0.U)
  val mideleg = RegInit(UInt(XLEN.W), 0.U)
  val mie = RegInit(0.U(XLEN.W))
  val mtvec = RegInit(UInt(XLEN.W), 0.U)
  val mcounteren = RegInit(UInt(XLEN.W), 0.U)

  val mscratch = RegInit(UInt(XLEN.W), 0.U)
  val mepc = RegInit(UInt(XLEN.W), 0.U)
  val mcause = RegInit(UInt(XLEN.W), 0.U)
  val mtval = RegInit(UInt(XLEN.W), 0.U)
  val mipWire = WireInit(0.U.asTypeOf(new Interrupt))
  val mipReg  = RegInit(0.U(64.W))
  //val mipFixMask = "h77f".U(64.W)
  val mip = (mipWire.asUInt | mipReg).asTypeOf(new Interrupt)

  private def GenMask(i: Int): UInt = GenMask(i, i)
  private def GenMask(i: Int, j: Int): UInt = ZeroExt(Fill(i - j + 1, true.B) << j, 64)
  val mstatusWMask = (~ZeroExt((
    GenMask(63)      | // SD is read-only
    GenMask(62, 38)  | // WPRI
    GenMask(37)      | // MBE is read-only
    GenMask(36)      | // SBE is read-only
    GenMask(35, 32)  | // SXL and UXL cannot be changed
    GenMask(31, 23)  | // WPRI
    GenMask(16, 15)  | // XS is read-only
    GenMask(6)       | // UBE, always little-endian (0)
    GenMask(4)       | // WPRI
    GenMask(2)       | // WPRI
    GenMask(0)         // WPRI
  ), 64)).asUInt
  val mstatusStruct = mstatus.asTypeOf(new StatusStruct)
  def mstatusUpdateSideEffect(mstatus: UInt): UInt = {
    val mstatusOld = WireInit(mstatus.asTypeOf(new StatusStruct))
    val mstatusNew = Cat(mstatusOld.fs === "b11".U, mstatus(XLEN-2, 0))
    mstatusNew
  }

  val pmpcfg0 = RegInit(UInt(XLEN.W), 0.U)
  val pmpcfg1 = RegInit(UInt(XLEN.W), 0.U)
  val pmpcfg2 = RegInit(UInt(XLEN.W), 0.U)
  val pmpcfg3 = RegInit(UInt(XLEN.W), 0.U)
  val pmpaddr0 = RegInit(UInt(XLEN.W), 0.U)
  val pmpaddr1 = RegInit(UInt(XLEN.W), 0.U)
  val pmpaddr2 = RegInit(UInt(XLEN.W), 0.U)
  val pmpaddr3 = RegInit(UInt(XLEN.W), 0.U)
  //val pmpaddrWmask = "h3fffffff".U(64.W) // 32bit physical address

  // Superviser-Level CSRs
  val sstatus = RegInit(UInt(XLEN.W), "h200000000".U)
  val sedeleg = RegInit(0.U(XLEN.W))
  val sideleg = RegInit(0.U(XLEN.W))
  val sie = RegInit(0.U(XLEN.W))
  val stvec = RegInit(UInt(XLEN.W), 0.U)
  val scounteren = RegInit(UInt(XLEN.W), 0.U)

  val sscratch = RegInit(UInt(XLEN.W), 0.U)
  val sepc = RegInit(UInt(XLEN.W), 0.U)
  val scause = RegInit(UInt(XLEN.W), 0.U)
  val stval = Reg(UInt(XLEN.W))
  val sipWire = WireInit(0.U.asTypeOf(new Interrupt))
  val sipReg  = RegInit(0.U(64.W))
  //val sipFixMask = "h77f".U(64.W)
  val sip = (sipWire.asUInt | sipReg).asTypeOf(new Interrupt)

  val satp = RegInit(UInt(XLEN.W), 0.U)

  val sieMask = "h222".U(64.W) & sideleg
  val sipMask  = "h222".U(64.W) & sideleg

  // User-Level CSRs
  val uepc = Reg(UInt(XLEN.W))

  // Atom LR/SC Control Bits
  val setLr = WireInit(Bool(), false.B)
  val setLrVal = WireInit(Bool(), false.B)
  val setLrAddr = WireInit(UInt(AddrBits.W), DontCare)
  val lr = RegInit(Bool(), false.B)
  val lrAddr = RegInit(UInt(AddrBits.W), 0.U)
  /*
  BoringUtils.addSink(setLr, "set_lr")
  BoringUtils.addSink(setLrVal, "set_lr_val")
  BoringUtils.addSink(setLrAddr, "set_lr_addr")
  BoringUtils.addSource(lr, "lr")
  BoringUtils.addSource(lrAddr, "lr_addr")

  when(setLr){
    lr := setLrVal
    lrAddr := setLrAddr
  }
  */

  // Hart Privilege Mode
  val privilegeMode = RegInit(UInt(2.W), ModeM)

  // CSR reg map
  val mapping = Map(

    // Supervisor Trap Setup
    MaskedRegMap(Sstatus, sstatus),
    MaskedRegMap(Sedeleg, sedeleg),
    MaskedRegMap(Sideleg, sideleg),
    MaskedRegMap(Sie, sie),
    MaskedRegMap(Stvec, stvec),
    MaskedRegMap(Scounteren, scounteren),

    // Supervisor Trap Handling
    MaskedRegMap(Sscratch, sscratch),
    MaskedRegMap(Sepc, sepc),
    MaskedRegMap(Scause, scause),
    MaskedRegMap(Stval, stval),
    MaskedRegMap(Sip, sip.asUInt, sipMask, MaskedRegMap.Unwritable, sipMask),

    // Supervisor Protection and Translation
    MaskedRegMap(Satp, satp),

    // Machine Information Registers
    MaskedRegMap(Mvendorid, mvendorid, 0.U, MaskedRegMap.Unwritable),
    MaskedRegMap(Marchid, marchid, 0.U, MaskedRegMap.Unwritable),
    MaskedRegMap(Mimpid, mimpid, 0.U, MaskedRegMap.Unwritable),
    MaskedRegMap(Mhartid, mhartid, 0.U, MaskedRegMap.Unwritable),

    // Machine Trap Setup
    MaskedRegMap(Mstatus, mstatus, mstatusWMask, mstatusUpdateSideEffect),
    MaskedRegMap(Misa, misa, 0.U, MaskedRegMap.Unwritable),
    MaskedRegMap(Medeleg, medeleg),
    MaskedRegMap(Mideleg, mideleg),
    MaskedRegMap(Mie, mie),
    MaskedRegMap(Mtvec, mtvec),
    MaskedRegMap(Mcounteren, mcounteren),

    // Machine Trap Handling
    MaskedRegMap(Mscratch, mscratch),
    MaskedRegMap(Mepc, mepc),
    MaskedRegMap(Mcause, mcause),
    MaskedRegMap(Mtval, mtval),
    MaskedRegMap(Mip, mip.asUInt, 0.U, MaskedRegMap.Unwritable),

    // Machine Memory Protection
    MaskedRegMap(Pmpcfg0, pmpcfg0),
    MaskedRegMap(Pmpcfg1, pmpcfg1),
    MaskedRegMap(Pmpcfg2, pmpcfg2),
    MaskedRegMap(Pmpcfg3, pmpcfg3),
    MaskedRegMap(PmpaddrBase + 0, pmpaddr0),
    MaskedRegMap(PmpaddrBase + 1, pmpaddr1),
    MaskedRegMap(PmpaddrBase + 2, pmpaddr2),
    MaskedRegMap(PmpaddrBase + 3, pmpaddr3)

  )

  val addr = io.src(1)(11, 0)
  val func = io.func
  val src1 = io.src(0)
  val csri = ZeroExt(io.zimm, XLEN)
  val rdata = Wire(UInt(XLEN.W))
  val wen = CSROpType.isCsrAccess(func) && io.valid
  val wdata = LookupTree(func, List(
    CSROpType.csrrw  -> src1,
    CSROpType.csrrs  -> (rdata | src1),
    CSROpType.csrrc  -> (rdata & ~src1),
    CSROpType.csrrwi -> csri,
    CSROpType.csrrsi -> (rdata | csri),
    CSROpType.csrrci -> (rdata & ~csri)
  ))

  MaskedRegMap.generate(mapping, addr, rdata, wen, wdata)
  val resetSatp = addr === Satp.U && wen // write to satp will cause the pipeline be flushed
  io.result := rdata

  // CSR inst decode
  val ret = Wire(Bool())
  val isEbreak = addr === privEbreak && CSROpType.isSystemOp(func) && io.valid
  val isEcall = addr === privEcall && CSROpType.isSystemOp(func) && io.valid
  val isMret = addr === privMret   && CSROpType.isSystemOp(func) && io.valid
  val isSret = addr === privSret   && CSROpType.isSystemOp(func) && io.valid
  val isUret = addr === privUret   && CSROpType.isSystemOp(func) && io.valid


  // Exception and Intr

  // interrupts
  val raiseIntr = mip.asUInt.orR
  val intrVec = 0.U(9.W)
  val intrNO = IntPriority.foldRight(0.U)((i: Int, sum: UInt) => Mux(intrVec(i), i.U, sum))

  // exceptions

  val csrExceptionVec = Wire(Vec(16, Bool()))
  csrExceptionVec.map(_ := false.B)
  csrExceptionVec(breakPoint) := io.valid && isEbreak
  csrExceptionVec(ecallM) := privilegeMode === ModeM && io.valid && isEcall
  csrExceptionVec(ecallS) := privilegeMode === ModeS && io.valid && isEcall
  csrExceptionVec(ecallU) := privilegeMode === ModeU && io.valid && isEcall
  //csrExceptionVec(illegalInstr) := (isIllegalAddr || isIllegalAccess) && wen //&& !io.isBackendException // Trigger an illegal instr exception when unimplemented csr is being read/written or not having enough privilege
  //csrExceptionVec(loadPageFault) := hasLoadPageFault
  //csrExceptionVec(storePageFault) := hasStorePageFault
  val iduExceptionVec = VecInit(Seq.fill(16)(false.B))//io.cfIn.exceptionVec
  val raiseExceptionVec = csrExceptionVec.asUInt | iduExceptionVec.asUInt
  val raiseException = raiseExceptionVec.orR
  val exceptionNO = ExcPriority.foldRight(0.U)((i: Int, sum: UInt) => Mux(raiseExceptionVec(i), i.U, sum))
  //io.wenFix := raiseException

  val causeNO = (raiseIntr << (XLEN-1)) | Mux(raiseIntr, intrNO, exceptionNO)
  //io.intrNO := Mux(raiseIntr, causeNO, 0.U)

  val raiseExceptionIntr = (raiseException || raiseIntr) && io.valid
  val retTarget = Wire(UInt(VAddrBits.W))
  val trapTarget = Wire(UInt(VAddrBits.W))
  io.redirect.valid := (io.valid && func === CSROpType.jmp) || raiseExceptionIntr || resetSatp
  io.redirect.target := Mux(resetSatp, io.pc + 4.U, Mux(raiseExceptionIntr, trapTarget, retTarget))

  // Branch control

  val deleg = Mux(raiseIntr, mideleg , medeleg)
  // val delegS = ((deleg & (1 << (causeNO & 0xf))) != 0) && (privilegeMode < ModeM);
  val delegS = (deleg(causeNO(3,0))) && (privilegeMode < ModeM)
  val hasInstrPageFault = false.B
  val hasLoadPageFault = false.B
  val hasStorePageFault = false.B
  val hasLoadAddrMisaligned = false.B
  val hasStoreAddrMisaligned = false.B
  val tvalWen = !(hasInstrPageFault || hasLoadPageFault || hasStorePageFault || hasLoadAddrMisaligned || hasStoreAddrMisaligned) || raiseIntr

  ret := isMret || isSret || isUret
  trapTarget := Mux(delegS, stvec, mtvec)(VAddrBits-1, 0)
  retTarget := DontCare
  // TODO redirect target
  // val illegalEret = TODO

  when (io.valid && isMret) {
    val mstatusOld = WireInit(mstatus.asTypeOf(new StatusStruct))
    val mstatusNew = WireInit(mstatus.asTypeOf(new StatusStruct))
    // mstatusNew.mpp.m := ModeU //TODO: add mode U
    mstatusNew.ie.m := mstatusOld.pie.m
    privilegeMode := mstatusOld.mpp
    mstatusNew.pie.m := true.B
    mstatusNew.mpp := ModeU
    mstatus := mstatusNew.asUInt
    lr := false.B
    retTarget := mepc(VAddrBits-1, 0)
  }

  when (io.valid && isSret) {
    val mstatusOld = WireInit(mstatus.asTypeOf(new StatusStruct))
    val mstatusNew = WireInit(mstatus.asTypeOf(new StatusStruct))
    // mstatusNew.mpp.m := ModeU //TODO: add mode U
    mstatusNew.ie.s := mstatusOld.pie.s
    privilegeMode := Cat(0.U(1.W), mstatusOld.spp)
    mstatusNew.pie.s := true.B
    mstatusNew.spp := ModeU
    mstatus := mstatusNew.asUInt
    lr := false.B
    retTarget := sepc(VAddrBits-1, 0)
  }

  when (io.valid && isUret) {
    val mstatusOld = WireInit(mstatus.asTypeOf(new StatusStruct))
    val mstatusNew = WireInit(mstatus.asTypeOf(new StatusStruct))
    // mstatusNew.mpp.m := ModeU //TODO: add mode U
    mstatusNew.ie.u := mstatusOld.pie.u
    privilegeMode := ModeU
    mstatusNew.pie.u := true.B
    mstatus := mstatusNew.asUInt
    retTarget := uepc(VAddrBits-1, 0)
  }

  when (raiseExceptionIntr) {
    val mstatusOld = WireInit(mstatus.asTypeOf(new StatusStruct))
    val mstatusNew = WireInit(mstatus.asTypeOf(new StatusStruct))

    when (delegS) {
      scause := causeNO
      sepc := SignExt(io.pc, XLEN)
      mstatusNew.spp := privilegeMode
      mstatusNew.pie.s := mstatusOld.ie.s
      mstatusNew.ie.s := false.B
      privilegeMode := ModeS
      when(tvalWen){stval := 0.U} // TODO: should not use =/=
      // printf("[*] mstatusNew.spp %x\n", mstatusNew.spp)
      // trapTarget := stvec(VAddrBits-1. 0)
    }.otherwise {
      mcause := causeNO
      mepc := SignExt(io.pc, XLEN)
      mstatusNew.mpp := privilegeMode
      mstatusNew.pie.m := mstatusOld.ie.m
      mstatusNew.ie.m := false.B
      privilegeMode := ModeM
      when(tvalWen){mtval := 0.U} // TODO: should not use =/=
      // trapTarget := mtvec(VAddrBits-1. 0)
    }
    // mstatusNew.pie.m := LookupTree(privilegeMode, List(
    //   ModeM -> mstatusOld.ie.m,
    //   ModeH -> mstatusOld.ie.h, //ERROR
    //   ModeS -> mstatusOld.ie.s,
    //   ModeU -> mstatusOld.ie.u
    // ))

    mstatus := mstatusNew.asUInt
  }

  /*
  val mode = RegInit(UInt(XLEN.W), 3.U)
  val mstatus = RegInit(UInt(XLEN.W), "ha00000000".U)
  val mcause = RegInit(UInt(XLEN.W), 0.U)
  val mepc = RegInit(UInt(XLEN.W), 0.U)
  val sstatus = RegInit(UInt(XLEN.W), "h200000000".U)
  val scause = RegInit(UInt(XLEN.W), 0.U)
  val sepc = RegInit(UInt(XLEN.W), 0.U)
  val satp = RegInit(UInt(XLEN.W), 0.U)

  val mip = RegInit(UInt(XLEN.W), 0.U)
  val mie = RegInit(UInt(XLEN.W), 0.U)
  val mscratch = RegInit(UInt(XLEN.W), 0.U)
  val sscratch = RegInit(UInt(XLEN.W), 0.U)
  val mideleg = RegInit(UInt(XLEN.W), 0.U)
  val medeleg = RegInit(UInt(XLEN.W), 0.U)

  val mtval = RegInit(UInt(XLEN.W), 0.U)
  val stval = RegInit(UInt(XLEN.W), 0.U)
  val mtvec = RegInit(UInt(XLEN.W), 0.U)
  val stvec = RegInit(UInt(XLEN.W), 0.U)
  // val mvendorid  = RegInit(UInt(XLEN.W), 0.U)
  // val marchid    = RegInit(UInt(XLEN.W), 0.U)
  // val mhartid    = RegInit(UInt(XLEN.W), 0.U)
  */
  layer.block(layers.Verification) {
    val csr = Wire(Vec(18, UInt(64.W)))
    csr(0) := privilegeMode
    csr(1) := mstatus
    csr(2) := sstatus
    csr(3) := mepc
    csr(4) := sepc
    csr(5) := mtval
    csr(6) := stval
    csr(7) := mtvec
    csr(8) := stvec
    csr(9) := mcause
    csr(10) := scause
    csr(11) := satp
    csr(12) := mip.asUInt
    csr(13) := mie
    csr(14) := mscratch
    csr(15) := sscratch
    csr(16) := mideleg
    csr(17) := medeleg

    val probeWire: CSRProbe = Wire(new CSRProbe(parameter))
    define(io.probe, ProbeValue(probeWire))
    probeWire.csr := csr
  }

}