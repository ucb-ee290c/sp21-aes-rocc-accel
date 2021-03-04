package aes

import chisel3._
import chiseltest._
import ee290cdma.{EE290CDMAReadIO, EE290CDMAWriteIO}
import org.scalatest.flatspec.AnyFlatSpec
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.tile.{RoCCCommand, RoCCResponse}
import verif._

import scala.util.Random

/*
Tests that verify basic functionality of the decoupler.
 */

class dmaSanityTest extends AnyFlatSpec with ChiselScalatestTester {
  implicit val p: Parameters = VerifTestUtils.getVerifParameters(xLen = 32) // Testing for our 32b RISC-V chip

  it should "elaborate the DMA <> TLXbar/TLRAM Standalone Block" in {
    val dut = LazyModule(new DMATLRAMStandaloneBlock)
    test(dut.module) { _ =>
      assert(true)
    }
  }

  it should "test DMA keyLoad256" in {
    val dut = LazyModule(new DMATLRAMStandaloneBlock)
    test(dut.module) { c =>
      // Initializing DMA driver, receiver (dummy), and monitor
//      val driver = new DecoupledDriverMaster[EE290CDMAWriteWriterReq](c.clock, dut.dma_in.write.req) // Used to send responses, takes in ValidTX
//      val monitor = new DecoupledMonitor[EE290CDMAReadIO](c.clock, dut.dma_in.read.req) // Used to observe all transactions on this interface
//      val receiver = new DecoupledDriverSlave[_](c.clock, dut.dma_in.read, 0) // Acts as a dummy receiver (does nothing but hold ready high)

      // TODO Write tests here

      assert(true)
    }
  }
}