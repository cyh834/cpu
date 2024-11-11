/** ************************************************************************************* Copyright (c) 2020-2021
  * Institute of Computing Technology, Chinese Academy of Sciences Copyright (c) 2020-2021 Peng Cheng Laboratory
  *
  * XiangShan is licensed under Mulan PSL v2. You can use this software according to the terms and conditions of the
  * Mulan PSL v2. You may obtain a copy of Mulan PSL v2 at: http://license.coscl.org.cn/MulanPSL2
  *
  * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING
  * BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
  *
  * See the Mulan PSL v2 for more details.
  */

package utility

import chisel3._
import chisel3.util._

object RegMap {
  def Unwritable = null
  def apply(addr: Int, reg: UInt, wfn: UInt => UInt = (x => x)) = (addr, (reg, wfn))
  def generate(
    mapping: Map[Int, (UInt, UInt => UInt)],
    raddr:   UInt,
    rdata:   UInt,
    waddr:   UInt,
    wen:     Bool,
    wdata:   UInt,
    wmask:   UInt
  ): Unit = {
    val chiselMapping = mapping.map { case (a, (r, w)) => (a.U, r, w) }
    rdata := LookupTree(raddr, chiselMapping.map { case (a, r, w) => (a, r) })
    chiselMapping.map { case (a, r, w) =>
      if (w != null) when(wen && waddr === a) { r := w(MaskData(r, wdata, wmask)) }
    }
  }
  def generate(mapping: Map[Int, (UInt, UInt => UInt)], addr: UInt, rdata: UInt, wen: Bool, wdata: UInt, wmask: UInt)
    : Unit = generate(mapping, addr, rdata, addr, wen, wdata, wmask)
}
