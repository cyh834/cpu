package cpu.elaborator

import mainargs._
import cpu.{CPU, CPUParameter}
import chisel3.experimental.util.SerializableModuleElaborator

object CPUMain extends SerializableModuleElaborator {
  val topName = "CPU"

  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  // 从命令行中读取参数
  @main
  case class CPUParameterMain(
    @arg(name = "extensions") extensions: Seq[String]) {
    def convert: CPUParameter = CPUParameter(extensions)
  }

  implicit def CPUParameterMainParser: ParserForClass[CPUParameterMain] =
    ParserForClass[CPUParameterMain]

  // 将参数写入json文件
  @main
  def config(
    @arg(name = "parameter") parameter:  CPUParameterMain,
    @arg(name = "target-dir") targetDir: os.Path = os.pwd
  ) =
    os.write.over(targetDir / s"${topName}.json", configImpl(parameter.convert))

  // 从json文件中读取参数
  @main
  def design(
    @arg(name = "parameter") parameter:  os.Path,
    @arg(name = "target-dir") targetDir: os.Path = os.pwd
  ) = {
    val (firrtl, annos) = designImpl[CPU, CPUParameter](os.read.stream(parameter))
    os.write.over(targetDir / s"${topName}.fir", firrtl)
    os.write.over(targetDir / s"${topName}.anno.json", annos)
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args.toIndexedSeq)
}
