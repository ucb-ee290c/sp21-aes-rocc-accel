package aes

import AESTestUtils._
import chisel3._
import chiseltest._
import chiseltest.internal.VerilatorBackendAnnotation
import chiseltest.experimental.TestOptionBuilder._
import designs.VerifRoCCStandaloneWrapper
import org.scalatest.flatspec.AnyFlatSpec
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.tile.{OpcodeSet, RoCCCommand, RoCCResponse}
import verif._


class accelSimpleTest extends AnyFlatSpec with ChiselScalatestTester {
  implicit val p: Parameters = VerifTestUtils.getVerifParameters(xLen = 32) // Testing for our 32b RISC-V chip


  it should "elaborate the accelerator + scratchpad memory" in {
    val dut = LazyModule(new AESAccelTLRAMStandaloneBlock)
    // Requires verilator backend! (For verilog blackbox files)
    test(dut.module).withAnnotations(Seq(VerilatorBackendAnnotation)) { _ =>
      assert(true)
    }
  }

  it should "elaborate the accelerator + scratchpad memory" in {
    val dut = LazyModule(new AESAccelTLRAMStandaloneBlock)
    // Requires verilator backend! (For verilog blackbox files)
    test(dut.module).withAnnotations(Seq(VerilatorBackendAnnotation)) { c =>
      // Initializing RoCCCommand driver
      val driver = new DecoupledDriverMaster[RoCCCommand](c.clock, dut.roccio.cmd)
      val txProto = new DecoupledTX(new RoCCCommand())
      val monitor = new DecoupledMonitor[RoCCResponse](c.clock, dut.roccio.resp)
      val receiver = new DecoupledDriverSlave[RoCCResponse](c.clock, dut.roccio.resp, 0)

      // TODO Write tests here

      assert(true)
    }
  }
}