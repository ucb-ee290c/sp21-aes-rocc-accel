// NOTE: Modified from verif/cosim/src/VerifRoCCStandalone.scala (https://github.com/TsaiAnson/verif)
// NOTE: Copied from verif/cosim/src/CosimTestUtils.scala
// NOTE: Modified from dsptools/rocket/src/main/scala/tl/Node.scala (https://github.com/ucb-bar/dsptools/blob/master/rocket/src/main/scala/tl/Node.scala)

// NOTE: Temporary file, will remove when integrated with Chipyard

package aes

import chisel3._
import chisel3.experimental.{IO}
import chisel3.util._
import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci.ClockSinkParameters
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import scala.collection.mutable.MutableList

object TLBundleBridgeImp extends BundleBridgeImp[TLBundle]

case class TLToBundleBridgeNode(managerParams: TLManagerPortParameters)(implicit valName: ValName)
  extends MixedAdapterNode(TLImp, TLBundleBridgeImp)(
    dFn = { masterParams =>
      BundleBridgeParams(() => TLBundle(TLBundleParameters(masterParams, managerParams)))
    },
    uFn = { mp => managerParams }
  )

object TLToBundleBridgeNode {
  def apply(managerParams: TLManagerParameters, beatBytes: Int)(implicit  valName: ValName): TLToBundleBridgeNode =
    new TLToBundleBridgeNode(TLManagerPortParameters(Seq(managerParams), beatBytes))
}

class TLToBundleBridge(managerParams: TLManagerPortParameters)(implicit p: Parameters) extends LazyModule {
  val node = TLToBundleBridgeNode(managerParams)
  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, _), (out, _)) =>
      out <> in
    }
  }
}

object TLToBundleBridge {
  def apply(managerParams: TLManagerPortParameters)(implicit p: Parameters): TLToBundleBridgeNode = {
    val converter = LazyModule(new TLToBundleBridge(managerParams))
    converter.node
  }
  def apply(managerParams: TLManagerParameters, beatBytes: Int)(implicit p: Parameters): TLToBundleBridgeNode = {
    apply(TLManagerPortParameters(Seq(managerParams), beatBytes))
  }
}

case class BundleBridgeToTLNode(clientParams: TLClientPortParameters)(implicit valName: ValName)
  extends MixedAdapterNode(TLBundleBridgeImp, TLImp)(
    dFn = { mp =>
      clientParams
    },
    uFn = { slaveParams => BundleBridgeNull() }
  )

object BundleBridgeToTLNode {
  def apply(clientParams: TLClientParameters, beatBytes: Int)(implicit valName: ValName): BundleBridgeToTLNode = {
    BundleBridgeToTLNode(TLClientPortParameters(Seq(clientParams), beatBytes))
  }
}

class BundleBridgeToTL(clientParams: TLClientPortParameters)(implicit p: Parameters) extends LazyModule {
  val node = BundleBridgeToTLNode(clientParams)
  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, _), (out, _)) =>
      out <> in
    }
  }
}

object BundleBridgeToTL {
  def apply(clientParams: TLClientPortParameters)(implicit p: Parameters): BundleBridgeToTLNode = {
    val converter = LazyModule(new BundleBridgeToTL(clientParams))
    converter.node
  }
  def apply(clientParams: TLClientParameters, beatBytes: Int)(implicit p: Parameters): BundleBridgeToTLNode = {
    apply(TLClientPortParameters(Seq(clientParams), beatBytes))
  }
}

case object VerifTileParams extends TileParams {
  val name: Option[String] = Some("verif_tile")
  val hartId: Int = 0
  val core: RocketCoreParams = RocketCoreParams()
  val clockSinkParams: ClockSinkParameters = ClockSinkParameters()
  val beuAddr: Option[BigInt] = None
  val blockerCtrlAddr: Option[BigInt] = None
  val btb: Option[BTBParams] = None
  val dcache: Option[DCacheParams] = Some(DCacheParams(rowBits=128)) // TODO: can these be derived from beat bytes, etc
  val icache: Option[ICacheParams] = Some(ICacheParams(rowBits=128))
}

object VerifTestUtils {
  def getVerifTLClientPortParameters(): TLClientPortParameters = {
    TLClientPortParameters(Seq(TLClientParameters("bundleBridgeToTL")))
  }

  def getVerifTLManagerPortParameters(beatBytes: Int = 16, pAddrBits: Int = 32,
                                    transferSize: TransferSizes = TransferSizes(1, 64)): TLManagerPortParameters = {
    TLManagerPortParameters(Seq(TLManagerParameters(address = Seq(AddressSet(0x0, BigInt("1"*pAddrBits, 2))),
      supportsGet = transferSize, supportsPutFull = transferSize, supportsPutPartial = transferSize)), beatBytes)
  }

  def getVerifTLBundleParameters(beatBytes: Int = 16, pAddrBits: Int = 32,
                             transferSize: TransferSizes = TransferSizes(1, 64)): TLBundleParameters = {
    TLBundleParameters(getVerifTLClientPortParameters(), getVerifTLManagerPortParameters(beatBytes, pAddrBits, transferSize)).copy(sourceBits = 5) //TOD: This is a hack, need to add some way to specify source bits in the bundle creaion process
  }

  def getVerifParameters(
      xLen: Int = 64,
      beatBytes: Int = 16,
      blockBytes: Int = 64,
      pAddrBits: Int = 32,
      transferSize: TransferSizes = TransferSizes(1, 64)): Parameters = {

    // val origParams = new RocketConfig //Parameters.empty //new freechips.rocketchip.subsystem.WithoutTLMonitors
    val origParams = new WithoutTLMonitors

    // augment the parameters
    implicit val p = origParams.alterPartial {
      case MonitorsEnabled => false
      case TileKey => VerifTileParams
      case XLen => xLen // Have to use xLen to avoid errors with naming
      case PgLevels => if (xLen == 64) 3 else 2
      case MaxHartIdBits => 1
      case SystemBusKey => SystemBusParams(
        beatBytes = beatBytes,
        blockBytes = blockBytes
      )
    }

    // TODO: should these be args to the main function as well for ease of use?
    def verifTLUBundleParams: TLBundleParameters = TLBundleParameters(addressBits = 64, dataBits = 64, sourceBits = 1,
      sinkBits = 1, sizeBits = 6,
      aUserBits = 6, dUserBits = 6,
      hasBCE = false)

    val dummyInNode = BundleBridgeSource(() => TLBundle(verifTLUBundleParams))
    val dummyOutNode = BundleBridgeSink[TLBundle]()

    val tlClientXbar = LazyModule(new TLXbar)
    val visibilityNode = TLEphemeralNode()(ValName("tile_Client"))

    visibilityNode :=* tlClientXbar.node
    tlClientXbar.node :=
      BundleBridgeToTL(getVerifTLClientPortParameters) :=
      dummyInNode

    dummyOutNode :=
      TLToBundleBridge(getVerifTLManagerPortParameters(beatBytes, pAddrBits, transferSize)) :=
      visibilityNode

    val outParams = p.alterPartial {
      case TileVisibilityNodeKey => visibilityNode
    }

    outParams
  }
}

class VerifRoCCStandaloneWrapper(dut: () => LazyRoCC, beatBytes: Int = 8, pAddrBits: Int = 32, addSinks: Int = 0, addSources: Int = 0)(implicit p: Parameters) extends LazyModule {
    lazy val ioOutNodes = new MutableList[BundleBridgeSink[TLBundle]]
    lazy val ioInNodes = new MutableList[BundleBridgeSource[TLBundle]]
    val dutInside = LazyModule(dut())
    val buffer = TLBuffer(BufferParams.default)

  lazy val module = new VerifRoCCStandaloneWrapperModule(this)
}

class VerifRoCCStandaloneWrapperModule(outer: VerifRoCCStandaloneWrapper) extends LazyModuleImp(outer) {
  import outer.dutInside
  import outer.ioInNodes
  import outer.ioOutNodes

  val io = IO(new RoCCIO(dutInside.nPTWPorts))
  io <> dutInside.module.io

  val tlOut = ioOutNodes.map{ (outNode) => outNode.makeIO()}
  val tlIn = ioInNodes.map{ (inNode) => inNode.makeIO()}
}
