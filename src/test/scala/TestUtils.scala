package aes

import chisel3._
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.tile.RoCCCommand
import verif.VerifBundleUtils.{RoCCCommandHelper, RoCCInstructionHelper}

package object AESTestUtils {
  def keyLoad128(addr: Int)(implicit p: Parameters): RoCCCommand = RoCCCommandHelper(inst = RoCCInstructionHelper(xs1 = true.B), rs1 = addr.U)
  def keyLoad256(addr: Int)(implicit p: Parameters): RoCCCommand = RoCCCommandHelper(inst = RoCCInstructionHelper(funct = 1.U, xs1 = true.B), rs1 = addr.U)
  def addrLoad(src: Int, dest: Int)(implicit p: Parameters): RoCCCommand = RoCCCommandHelper(inst = RoCCInstructionHelper(funct = 2.U, xs1 = true.B, xs2 = true.B),
    rs1 = src.U, rs2 = dest.U)
  def encBlock(count: Int)(implicit p: Parameters): RoCCCommand = RoCCCommandHelper(inst = RoCCInstructionHelper(funct = 3.U, xs1 = true.B), rs1 = count.U)
  def decBlock(count: Int)(implicit p: Parameters): RoCCCommand = RoCCCommandHelper(inst = RoCCInstructionHelper(funct = 4.U, xs1 = true.B), rs1 = count.U)
  def getStatus(rd: Int)(implicit p: Parameters): RoCCCommand = {
    assert(rd < 32, s"RD register must be less than 32. Given: $rd")
    RoCCCommandHelper(inst = RoCCInstructionHelper(funct = 5.U, xd = true.B, rd = rd.U))
  }
  def getInterrupt(rd: Int)(implicit p: Parameters): RoCCCommand = {
    assert(rd < 32, s"RD register must be less than 32. Given: $rd")
    RoCCCommandHelper(inst = RoCCInstructionHelper(funct = 6.U, xd = true.B, rd = rd.U))
  }
}