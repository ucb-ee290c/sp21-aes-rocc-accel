package aes

import chisel3._
import chisel3.util.HasBlackBoxResource
import chipsalliance.rocketchip.config.Parameters
import chipsalliance.rocketchip.config.Config
import ee290cdma.EE290CDMA
import freechips.rocketchip.tile.{BuildRoCC, HasCoreParameters, LazyRoCC, LazyRoCCModuleImp, OpcodeSet}
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.subsystem.SystemBusKey

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

class AESAccelImp(outer: AESAccel)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) with HasCoreParameters {
  val beatBytes = p(SystemBusKey).beatBytes

  val dcplr = Module(new RoCCDecoupler)
  dcplr.io.reset     := reset.asBool()
  dcplr.io.rocc_cmd  <> io.cmd
  io.resp            <> dcplr.io.rocc_resp
  io.busy            := dcplr.io.rocc_busy
  io.interrupt       := dcplr.io.rocc_intr
  dcplr.io.rocc_excp := io.exception

  val ctrl = Module(new AESController(paddrBits, beatBytes))
  ctrl.io.reset   := reset.asBool()
  ctrl.io.dcplrIO <> dcplr.io.ctrlIO

  val dma = LazyModule(new EE290CDMA(beatBytes, 256, "AESAccelDMA"))
  dma.module.io.read.req <> ctrl.io.dmem.readReq
  dma.module.io.write.req <> ctrl.io.dmem.writeReq
  ctrl.io.dmem.readResp <> dma.module.io.read.resp
  ctrl.io.dmem.readRespQueue <> dma.module.io.read.queue
  ctrl.io.dmem.busy := dma.module.io.readBusy | dma.module.io.writeBusy

  val aesbb = Module(new aes)
  aesbb.io <> ctrl.io.aesCoreIO
}
