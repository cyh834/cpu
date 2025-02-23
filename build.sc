// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package build

import mill._
import mill.scalalib._
import mill.define.{Command, TaskModule}
import mill.scalalib.publish._
import mill.scalalib.scalafmt._
import mill.scalalib.TestModule.Utest
import mill.util.Jvm
import coursier.maven.MavenRepository

object deps {
  val scalaVer = "2.13.15"
  val mainargs = ivy"com.lihaoyi::mainargs:0.5.0"
  val oslib = ivy"com.lihaoyi::os-lib:0.9.1"
  val upickle = ivy"com.lihaoyi::upickle:3.3.1"
  val chisel = ivy"org.chipsalliance::chisel::0.0.0+0-no-vcs-SNAPSHOT"
  val chiselPlugin = ivy"org.chipsalliance:chisel-plugin_${scalaVer}:0.0.0+0-no-vcs-SNAPSHOT"
  val rvdecoderdb = ivy"me.jiuyang::rvdecoderdb-jvm:0.0.0+0-no-vcs-SNAPSHOT"
}

object rvdecoderdb extends RVDecoderDB
trait RVDecoderDB extends millbuild.dependencies.rvdecoderdb.common.RVDecoderDBJVMModule with ScalaModule {
  def scalaVersion            = T(deps.scalaVer)
  def osLibIvy                = deps.oslib
  def upickleIvy              = deps.upickle
  override def millSourcePath = os.pwd / "dependencies" / "rvdecoderdb" / "rvdecoderdb"
}

object cpu extends CPU
trait CPU extends common.HasChisel with ScalafmtModule{
  def scalaVersion = T(deps.scalaVer)

  def rvdecoderdbIvy = v.rvdecoderdb
  def riscvOpcodesPath  = T.input(PathRef(millSourcePath / os.up / "dependencies" / "riscv-opcodes"))

  def chiselModule = None
  def chiselPluginJar = Task(None)
  def chiselIvy = Some(deps.chisel)
  def chiselPluginIvy = Some(deps.chiselPlugin)
}

object elaborator extends Elaborator
trait Elaborator extends common.ElaboratorModule with ScalafmtModule {
  def scalaVersion = Task(deps.scalaVer)

  def circtInstallPath =
    Task.input(PathRef(os.Path(T.ctx().env("CIRCT_INSTALL_PATH"))))

  def generators = Seq(cpu)

  def mainargsIvy = deps.mainargs

  def chiselModule = None
  def chiselPluginJar = Task(None)
  def chiselPluginIvy = Some(deps.chiselPlugin)
  def chiselIvy = Some(deps.chisel)
}