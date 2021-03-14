package aes

import chisel3._
import chiseltest._
import chiseltest.internal.{VerilatorBackendAnnotation, WriteVcdAnnotation}
import chiseltest.experimental.TestOptionBuilder._
import org.scalatest.flatspec.AnyFlatSpec
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.tile.{RoCCCommand, RoCCResponse}
import verif._


class AccelSimpleTest extends AnyFlatSpec with ChiselScalatestTester {
  implicit val p: Parameters = VerifTestUtils.getVerifParameters(xLen = 32) // Testing for our 32b RISC-V chip

  // Elaborate to see if anything broke
  it should "elaborate the accelerator" in {
    val dut = LazyModule(new AESAccelStandaloneBlock)
    // Requires verilator backend! (For verilog blackbox files)
    test(dut.module).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      assert(true)
    }
  }

  it should "Test 128b AES Encryption" in {
    val dut = LazyModule(new AESAccelStandaloneBlock)
    test(dut.module).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      // RoCCCommand driver + RoCCResponse receiver
      val driver = new DecoupledDriverMaster[RoCCCommand](c.clock, c.io.cmd)
      val txProto = new DecoupledTX(new RoCCCommand())
      val monitor = new DecoupledMonitor[RoCCResponse](c.clock, c.io.resp)
      val receiver = new DecoupledDriverSlave[RoCCResponse](c.clock, c.io.resp, 0)

      // Mock Memory
      val slaveFn = new TLMemoryModel(dut.to_mem.params)
      val slaveModel = new TLDriverSlave(c.clock, dut.to_mem, slaveFn, TLMemoryModel.State.empty())
      val slaveMonitor = new TLMonitor(c.clock, dut.to_mem)

      // TODO

      assert(true)
    }
  }
}