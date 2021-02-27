package aes

import org.scalatest._
import chisel3._
import chisel3.experimental.BundleLiterals._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import freechips.rocketchip.config.Parameters
import verif._

class dcplrSanityTest extends AnyFlatSpec with ChiselScalatestTester {
  it should "elaborate the Decoupler" in {
    implicit val p: Parameters = VerifTestUtils.getVerifParameters()

    // .withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation))
    test(new RoCCDecoupler()) { c =>
      assert(true)
    }
  }

  
}