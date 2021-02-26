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
  dcplr.io.rocc_cmd  <> io.cmd
  dcplr.io.rocc_resp <> io.resp
  io.busy            := dcplr.io.rocc_busy
  io.interrupt       := dcplr.io.rocc_intr
  dcplr.io.rocc_excp := io.exception

  val ctrl = new AESController
  ctrl.io.dcplrIO <> dcplr.io.ctrlIO

  ctrl.io.dmem.req <> io.mem.req
  ctrl.io.dmem.resp <> io.mem.resp
  // TODO: initlize io.mem.<wires>

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
