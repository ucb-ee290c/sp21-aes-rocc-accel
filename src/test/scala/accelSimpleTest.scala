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
import freechips.rocketchip.tile.OpcodeSet
import verif._


class accelSimpleTest extends AnyFlatSpec with ChiselScalatestTester {
  implicit val p: Parameters = VerifTestUtils.getVerifParameters(xLen = 32) // Testing for our 32b RISC-V chip

  val dut = LazyModule(new VerifRoCCStandaloneWrapper(() => new AESAccel(OpcodeSet.custom0)))

  it should "elaborate the accelerator" in {
    // Requires verilator backend! (For verilog blackbox files)
    test(dut.module).withAnnotations(Seq(VerilatorBackendAnnotation)) { _ =>
      assert(true)
    }
  }
}