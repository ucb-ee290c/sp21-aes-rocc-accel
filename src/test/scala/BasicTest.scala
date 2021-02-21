import org.scalatest._

import chisel3._
import chisel3.experimental.BundleLiterals._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import freechips.rocketchip.subsystem.WithoutTLMonitors
//import freechips.rocketchip.subsystem.WithoutFPU

class BasicTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "RoCC Decoupler"
  
  it should "Decode the instruction" in {
    implicit val p = new WithoutTLMonitors
    test (new RoCCDecoupler()) { c =>
        c.io.rocc_io.cmd.valid.poke(true.B)
        c.io.rocc_io.cmd.bits.inst.funct.poke(0.U) // AES 128
        c.io.rocc_io.cmd.bits.rs1.poke(20.U)
        c.clock.step()
        c.key_valid_reg.expect(true.U)
      }
    }
  }
      
}
