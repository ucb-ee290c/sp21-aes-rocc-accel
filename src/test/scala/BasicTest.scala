import org.scalatest._

import chisel3._
import chisel3.experimental.BundleLiterals._
import chiseltest._
import chiseltest.experimental.TestOptionBuilder._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import freechips.rocketchip.subsystem.WithoutTLMonitors
import freechips.rocketchip.config.Parameters
import chiseltest.internal.WriteVcdAnnotation
import freechips.rocketchip.tile.OpcodeSet
import freechips.rocketchip.diplomacy.LazyModule
import chiseltest.internal.VerilatorBackendAnnotation

class BasicTest extends AnyFlatSpec with ChiselScalatestTester {
  
  it should "elaborate the Decoupler" in {
    implicit val p: Parameters = VerifTestUtils.getVerifParameters()

    // .withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation))
    test(new RoCCDecoupler()) { c =>
      assert(true)
    }
  }

  it should "elaborate the Controller" in {
    implicit val p: Parameters = VerifTestUtils.getVerifParameters()

    //.withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation))
    test(new AESController()) { c =>
      assert(true)
    }
  }

  // it should "elaborate the RoCCAccelerator" in {
  //   implicit val p: Parameters = VerifTestUtils.getVerifParameters()
  //   val dut = LazyModule(new VerifRoCCStandaloneWrapper(() => new AESAccel(OpcodeSet.custom0)))

  //   test(dut.module).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
  //     assert(true)
  //   }
  // }

  // it should "Decode the instruction" in {
  //   implicit val p = new WithoutTLMonitors
  //   test (new RoCCDecoupler()) { c =>
  //       c.io.rocc_io.cmd.valid.poke(true.B)
  //       c.io.rocc_io.cmd.bits.inst.funct.poke(0.U) // AES 128
  //       c.io.rocc_io.cmd.bits.rs1.poke(20.U)
  //       c.clock.step()
  //       c.key_valid_reg.expect(true.U)
  //     }
  //   }
  // }
}
