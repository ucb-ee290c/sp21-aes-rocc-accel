package aes

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

class dcplrSanityTest extends AnyFlatSpec with ChiselScalatestTester {
  it should "elaborate the Decoupler" in {
    implicit val p: Parameters = VerifTestUtils.getVerifParameters()

    // .withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation))
    test(new RoCCDecoupler()) { c =>
      assert(true)
    }
  }

  
}