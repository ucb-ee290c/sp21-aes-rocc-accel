package aes

import chisel3._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile.OpcodeSet
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem.WithoutTLMonitors
import ee290cdma._

// TODO: Temporary declaration here, solve strange compilation error
object AESDefaultTLParams {
  def slave: TLSlavePortParameters = TLSlavePortParameters.v1(
    Seq(
      TLSlaveParameters.v1( // TL-UH master
        address = Seq(AddressSet(0x0, 0xfff)),
        supportsGet = TransferSizes(1, 32),
        supportsPutFull = TransferSizes(1, 32),
        supportsPutPartial = TransferSizes(1, 32),
        supportsLogical = TransferSizes(1, 32),
        supportsArithmetic = TransferSizes(1, 32),
        supportsHint = TransferSizes(1, 32),
        regionType = RegionType.UNCACHED)
    ),
    beatBytes = 8)

  def master(name: String = "TLMasterPort", idRange: IdRange = IdRange(0,1)): TLMasterPortParameters = TLMasterPortParameters.v1(
    Seq(
      TLMasterParameters.v1(name = name, sourceId = idRange)
    ))

  // Temporary cache parameters
  def slaveCache: TLSlavePortParameters = TLSlavePortParameters.v1(
    Seq(
      TLSlaveParameters.v1(
        address = Seq(AddressSet(0x0, 0xfff)),
        supportsGet = TransferSizes(1, 32),
        supportsPutFull = TransferSizes(1, 32),
        supportsPutPartial = TransferSizes(1, 32),
        supportsLogical = TransferSizes(1, 32),
        supportsArithmetic = TransferSizes(1, 32),
        supportsHint = TransferSizes(1, 32),
        supportsAcquireB = TransferSizes(1, 32),
        supportsAcquireT = TransferSizes(1, 32),
        regionType = RegionType.UNCACHED
      )
    ),
    endSinkId = 1, beatBytes = 8)

  def masterCache: TLMasterPortParameters = TLMasterPortParameters.v1(
    Seq(
      TLMasterParameters.v1(
        name = "TestBundle",
        supportsProbe = TransferSizes(1, 32),
        supportsGet = TransferSizes(1, 32),
        supportsPutFull = TransferSizes(1, 32),
        supportsPutPartial = TransferSizes(1, 32),
        supportsLogical = TransferSizes(1, 32),
        supportsArithmetic = TransferSizes(1, 32),
        supportsHint = TransferSizes(1, 32)
      )
    ))
}

class DMATLRAMStandaloneBlock(implicit p: Parameters = new WithoutTLMonitors) extends LazyModule {
  val mPortParams = AESDefaultTLParams.master()
  val sPortParams = AESDefaultTLParams.slave
  val bParams= TLBundleParameters(mPortParams, sPortParams)

  // NOTE: Hardcoded address set for now
  val ram  = LazyModule(new TLRAM(AddressSet(0x0, 0x1ff), cacheable = false, atomics = true, beatBytes = 8))
  val xbar = LazyModule(new TLXbar)
  val dma  = LazyModule(new EE290CDMA(sPortParams.beatBytes, 256, "TLRAMDMA Test"))

  // Test IO for debug/initializing TLRAM
  val ioInNode = BundleBridgeSource(() => TLBundle(bParams))
  val test_in = InModuleBody { ioInNode.makeIO() }
  // IO for DMA
  val dma_in = InModuleBody{ dma.module.io }

  ram.node := TLBuffer() := xbar.node
  xbar.node := dma.id_node
  xbar.node := BundleBridgeToTL(mPortParams) := ioInNode

  lazy val module = new LazyModuleImp(this) {}
}

class AESAccelTLRAMStandaloneBlock(implicit p: Parameters = new WithoutTLMonitors) extends LazyModule {
  val mPortParams = AESDefaultTLParams.master()
  val sPortParams = AESDefaultTLParams.slave
  val bParams = TLBundleParameters(mPortParams, sPortParams)

  // NOTE: Hardcoded address set for now
  val ram  = LazyModule(new TLRAM(AddressSet(0x0, 0x1ff), cacheable = false, atomics = true, beatBytes = 8))
  val xbar = LazyModule(new TLXbar)
  val aes = LazyModule(new AESAccel(OpcodeSet.custom0))
  xbar.node := aes.module.dma.id_node
  // IO For decoupler
  val roccio = InModuleBody{ aes.module.io }

  lazy val module = new LazyModuleImp(this) {}
}