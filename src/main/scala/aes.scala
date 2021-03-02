package aes

import chisel3._
import chisel3.util.HasBlackBoxResource
import chipsalliance.rocketchip.config.Parameters
import chipsalliance.rocketchip.config.Config
import ee290cdma.EE290CDMA
import freechips.rocketchip.tile.LazyRoCC
import freechips.rocketchip.tile.LazyRoCCModuleImp
import freechips.rocketchip.tile.OpcodeSet
import freechips.rocketchip.tile.BuildRoCC
import freechips.rocketchip.diplomacy.LazyModule

// Blackbox (Class name must match top-level verilog file)
class aes(implicit p: Parameters) extends BlackBox with HasBlackBoxResource {
  val io = IO(new AESCoreIO)

  addResource("/vsrc/aes_core.v")
  addResource("/vsrc/aes_decipher_block.v")
  addResource("/vsrc/aes_encipher_block.v")
  addResource("/vsrc/aes_inv_sbox.v")
  addResource("/vsrc/aes_key_mem.v")
  addResource("/vsrc/aes_sbox.v")
  addResource("/vsrc/aes.v")
}

class AESAccel(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes = opcodes, nPTWPorts = 0) {
  override lazy val module = new AESAccelImp(this)
}

class AESAccelImp(outer: AESAccel)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) {
  val dcplr = Module(new RoCCDecoupler)
  dcplr.io.reset     := reset.asBool()
  dcplr.io.rocc_cmd  <> io.cmd
  io.resp            <> dcplr.io.rocc_resp
  io.busy            := dcplr.io.rocc_busy
  io.interrupt       := dcplr.io.rocc_intr
  dcplr.io.rocc_excp := io.exception

  val ctrl = Module(new AESController)
  ctrl.io.reset   := reset.asBool()
  ctrl.io.dcplrIO <> dcplr.io.ctrlIO

  io.mem.req        <> ctrl.io.dmem.req
  ctrl.io.dmem.resp <> io.mem.resp
  // TODO: initlize io.mem.<wires>

  val aesbb = Module(new aes)
  aesbb.io <> ctrl.io.aesCoreIO

  // DMA
  // NOTE: Hardcoded beatbytes for now
  val dma = LazyModule(new EE290CDMA(8, 256, "AESAccelDMA"))
  // TODO: Fix up IO
}

class WithAESAccel extends Config((site, here, up) => {
  case BuildRoCC => up(BuildRoCC) ++ Seq(
    (p: Parameters) => {
      val aes = LazyModule.apply(new AESAccel(OpcodeSet.custom0)(p))
      aes
    }
  )
})
