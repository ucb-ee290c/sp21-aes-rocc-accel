import chisel3._
import chisel3.util.HasBlackBoxResource
import chipsalliance.rocketchip.config.Parameters
import chipsalliance.rocketchip.config.Config
import freechips.rocketchip.tile.LazyRoCC
import freechips.rocketchip.tile.LazyRoCCModuleImp
import freechips.rocketchip.tile.OpcodeSet
import freechips.rocketchip.tile.BuildRoCC
import freechips.rocketchip.diplomacy.LazyModule

class AESCoreBlackBox(implicit p: Parameters) extends BlackBox with HasBlackBoxResource {
  val io = IO(new AESCoreIO)

  addResource("/vsrc/aes.v")
}

class AESAccel(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes = opcodes, nPTWPorts = 0) {
  override lazy val module = new AESAccelImp(this)
}

class AESAccelImp(outer: AESAccel)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) {
  val dcplr = new RoCCDecoupler
  dcplr.io.rocc_io <> io

  val ctrl = new AESController
  ctrl.io.dcplrIO <> dcplr.io.ctrlIO
  ctrl.io.dmem <> dcplr.io.dmem

  val aesbb = new AESCoreBlackBox
  aesbb.io <> ctrl.io.aesCoreIO
}

class WithAESAccel extends Config((site, here, up) => {
  case BuildRoCC => up(BuildRoCC) ++ Seq(
    (p: Parameters) => {
      val aes = LazyModule.apply(new AESAccel(OpcodeSet.custom0)(p))
      aes
    }
  )
})
