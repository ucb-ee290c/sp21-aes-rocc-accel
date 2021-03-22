package aes

import chisel3._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile.OpcodeSet
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem.WithoutTLMonitors
import verif.VerifTestUtils

class AESAccelStandaloneBlock(beatBytes: Int)(implicit p: Parameters = new WithoutTLMonitors) extends LazyModule {
  val mPortParams = VerifTestUtils.getVerifTLMasterPortParameters()
  val sPortParams = VerifTestUtils.getVerifTLSlavePortParameters(beatBytes = beatBytes)
  val bParams = TLBundleParameters(mPortParams, sPortParams)

  // AES Accelerator
  val aes = LazyModule(new AESAccel(OpcodeSet.custom0))

  // IO for DMA
  val ioOutNode = BundleBridgeSink[TLBundle]()
  val to_mem = InModuleBody { ioOutNode.makeIO() }

  ioOutNode := TLToBundleBridge(sPortParams) := aes.tlNode

  lazy val module = new LazyModuleImp(this) {
    val io = IO(chiselTypeOf(aes.module.io))
    io <> aes.module.io
  }
}
