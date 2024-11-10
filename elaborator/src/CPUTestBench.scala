package cpu.elaborator

import mainargs._
import cpu.{CPUTestBench, CPUTestBenchParameter, TestVerbatimParameter}
import cpu.elaborator.CPUMain.CPUParameterMain
import chisel3.experimental.util.SerializableModuleElaborator

object CPUTestBenchMain extends SerializableModuleElaborator {
  val topName = "CPUTestBench"

  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  @main
  case class CPUTestBenchParameterMain(
    @arg(name = "testVerbatimParameter") testVerbatimParameter: TestVerbatimParameterMain,
    @arg(name = "cpuParameter") cpuParameter:                   CPUParameterMain,
    @arg(name = "timeout") timeout:                             Int,
    @arg(name = "testSize") testSize:                           Int) {
    def convert: CPUTestBenchParameter = CPUTestBenchParameter(
      testVerbatimParameter.convert,
      cpuParameter.convert,
      timeout,
      testSize
    )
  }

  case class TestVerbatimParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset:       Boolean,
    @arg(name = "initFunctionName") initFunctionName: String,
    @arg(name = "dumpFunctionName") dumpFunctionName: String,
    @arg(name = "clockFlipTick") clockFlipTick:       Int,
    @arg(name = "resetFlipTick") resetFlipTick:       Int) {
    def convert: TestVerbatimParameter = TestVerbatimParameter(
      useAsyncReset:    Boolean,
      initFunctionName: String,
      dumpFunctionName: String,
      clockFlipTick:    Int,
      resetFlipTick:    Int
    )
  }

  implicit def TestVerbatimParameterMainParser: ParserForClass[TestVerbatimParameterMain] =
    ParserForClass[TestVerbatimParameterMain]

  implicit def CPUParameterMainParser: ParserForClass[CPUParameterMain] =
    ParserForClass[CPUParameterMain]

  implicit def CPUTestBenchParameterMainParser: ParserForClass[CPUTestBenchParameterMain] =
    ParserForClass[CPUTestBenchParameterMain]

  @main
  def config(
    @arg(name = "parameter") parameter:  CPUTestBenchParameterMain,
    @arg(name = "target-dir") targetDir: os.Path = os.pwd
  ) =
    os.write.over(targetDir / s"${topName}.json", configImpl(parameter.convert))

  @main
  def design(
    @arg(name = "parameter") parameter:  os.Path,
    @arg(name = "target-dir") targetDir: os.Path = os.pwd
  ) = {
    val (firrtl, annos) = designImpl[CPUTestBench, CPUTestBenchParameter](os.read.stream(parameter))
    os.write.over(targetDir / s"${topName}.fir", firrtl)
    os.write.over(targetDir / s"${topName}.anno.json", annos)
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args.toIndexedSeq)
}