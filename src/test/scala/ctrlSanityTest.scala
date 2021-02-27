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


class ctrlSanityTest extends AnyFlatSpec with ChiselScalatestTester {
  it should "elaborate the Controller" in {
    implicit val p: Parameters = VerifTestUtils.getVerifParameters()

    //.withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation))
    test(new AESController()) { c =>
      assert(true)
    }
  }

  it should "test controller state transitions" in {
    implicit val p: Parameters = VerifTestUtils.getVerifParameters()
    val num_block = 2
    test(new AESController()) { c =>
      c.io.dmem.req.ready.poke(true.B) // memory always ready
      c.io.dmem.resp.valid.poke(true.B) // memory resp always valid
      c.io.dcplrIO.key_valid.poke(true.B) // decoupler key always valid
      c.io.dcplrIO.addr_valid.poke(true.B) // decoupler addr always valid
      c.io.dcplrIO.start_valid.poke(true.B) // decoupler addr always valid
      // set decoupler register
      c.io.dcplrIO.key_addr.poke(20.U) //
      c.io.dcplrIO.key_size.poke(0.U) //
      c.io.dcplrIO.src_addr.poke(40.U) //
      c.io.dcplrIO.dest_addr.poke(60.U) //
      c.io.dcplrIO.op_type.poke(false.B) //
      c.io.dcplrIO.block_count.poke(num_block.U) //
      
      println("Start AES process")
      c.clock.step(1) // start AES process

      c.clock.step(9) // 9 cycles to load key
      println("Load key done")
      c.io.dcplrIO.key_valid.poke(false.B) // load key done, set key_valid false
      c.clock.step(4) // 4 cycles to expand key
      c.io.aesCoreIO.read_data.poke(1.U) // set expand done
      c.clock.step(1)
      println("Expand key done")
      c.io.aesCoreIO.read_data.poke(0.U) // unset ready
      c.clock.step(1)
      for (i <- 1 to num_block) {
        println("--- round " + i + " ---")
        c.clock.step(10) // 10 cycles to load text
        println("Load text done")

        c.clock.step(10) // 10 cycles to do enc/dec
        c.io.aesCoreIO.read_data.poke(1.U) // set dec/enc done
        c.clock.step(1)
        println("Compute done")
        c.io.aesCoreIO.read_data.poke(0.U) // unset ready
        c.clock.step(11) // 11 cycle to write result
        println("Write result done")
      }

      println("------- ALL DONE -------")
    }
  }
}