package aes

import org.scalatest._
import chisel3._
import chisel3.experimental.BundleLiterals._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import freechips.rocketchip.config.Parameters
import verif._

import scala.util.Random


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
    val key_addr = 20.U
    var src_addr = 40
    var dest_addr = 100
    val op_type = true.B
    val num_block = 3
    var counter = 0

    test(new AESController()) { c =>
      c.io.dmem.req.ready.poke(false.B) // memory always ready
      c.io.dmem.resp.valid.poke(false.B) // memory resp always valid
      c.io.dcplrIO.key_valid.poke(false.B) // decoupler key always valid
      c.io.dcplrIO.addr_valid.poke(false.B) // decoupler addr always valid
      c.io.dcplrIO.start_valid.poke(false.B) // decoupler addr always valid
      c.io.aesCoreIO.read_data.poke(1.U) // aes ready

      c.io.reset.poke(true.B)
      c.clock.step() 
      c.io.reset.poke(false.B)

      /* IDLE */
      assert(c.io.ctestState.peek.litValue() == AESState.sIdle.litValue())
      // start AES process
      assert(c.io.dcplrIO.key_ready.peek.litToBoolean)
      c.io.dcplrIO.key_valid.poke(true.B)
      c.io.dcplrIO.key_addr.poke(key_addr)
      c.io.dcplrIO.key_size.poke(0.U)
      c.clock.step() 
      assert(!c.io.dcplrIO.key_ready.peek.litToBoolean)
      c.io.dcplrIO.key_valid.poke(false.B) // load key done, set key_valid false

      /* KEY SETUP */
      assert(c.io.ctestState.peek.litValue() == AESState.sKeySetup.litValue())
      c.clock.step(2) // idle for 2 cycles

      // wait for memory loading key
      c.io.dmem.req.ready.poke(true.B) // memory always ready
      c.io.dmem.resp.valid.poke(true.B) // memory resp always valid
      while (c.io.testCounter.peek.litValue() != 4) {
        assert (c.io.dmem.req.valid.peek.litToBoolean)
        // check memory address is correct
        assert (c.io.dmem.req.bits.addr.peek.litValue() == (key_addr.litValue() + 4 * counter))
        c.clock.step()
        // check aes address is correct
        assert(c.io.ctestState.peek.litValue() == AESState.sKeySetup.litValue())
        assert (c.io.aesCoreIO.cs.peek.litToBoolean)
        assert (c.io.aesCoreIO.we.peek.litToBoolean)
        assert (c.io.aesCoreIO.address.peek.litValue() == AESAddr.KEY.litValue() + counter)
        counter = counter + 1
        c.clock.step()
      }
      counter = 0
      c.io.dmem.req.ready.poke(false.B)
      c.io.dmem.resp.valid.poke(false.B)
      c.clock.step()

      // set key init register
      assert (c.io.aesCoreIO.cs.peek.litToBoolean )
      assert (c.io.aesCoreIO.we.peek.litToBoolean )
      assert (c.io.aesCoreIO.address.peek.litValue == AESAddr.CTRL.litValue())
      c.clock.step()
      c.io.aesCoreIO.read_data.poke(0.U) // aes expanding
      c.clock.step()

      /* KEY EXPANSION */
      assert (c.io.ctestState.peek.litValue() == AESState.sKeyExp.litValue()) 
      c.clock.step(5) // cycles to expand key
      c.io.aesCoreIO.read_data.poke(1.U) // aes expand done
      c.clock.step()
      
      /* WAIT DATA */
      assert (c.io.ctestState.peek.litValue() == AESState.sWaitData.litValue())
      c.clock.step(2) // idle for 2 cycles

      assert (c.io.dcplrIO.addr_ready.peek.litToBoolean)
      c.io.dcplrIO.addr_valid.poke(true.B)
      c.io.dcplrIO.src_addr.poke(src_addr.asUInt)
      c.io.dcplrIO.dest_addr.poke(dest_addr.asUInt)
      c.clock.step()

      /* DATA SETUP */
      assert (c.io.ctestState.peek.litValue() == AESState.sDataSetup.litValue())
      assert (!c.io.dcplrIO.addr_ready.peek.litToBoolean)
      
      // wait for memory loading text
      c.io.dmem.req.ready.poke(true.B) // memory always ready
      c.io.dmem.resp.valid.poke(true.B) // memory resp always valid
      while (c.io.testCounter.peek.litValue() != 4) {
        assert (c.io.dmem.req.valid.peek.litToBoolean)
        // check memory address is correct
        assert (c.io.dmem.req.bits.addr.peek.litValue() == (src_addr + 4 * counter))
        c.clock.step()
        // check aes address is correct
        assert(c.io.ctestState.peek.litValue() == AESState.sDataSetup.litValue())
        assert (c.io.aesCoreIO.cs.peek.litToBoolean)
        assert (c.io.aesCoreIO.we.peek.litToBoolean)
        assert (c.io.aesCoreIO.address.peek.litValue() == AESAddr.TEXT.litValue() + counter)
        counter = counter + 1
        c.clock.step()
      }
      counter = 0
      c.io.dmem.req.ready.poke(false.B) // memory always ready
      c.io.dmem.resp.valid.poke(false.B) // memory resp always valid
      c.clock.step()
      assert (c.io.mtestState.peek.litValue() == MemState.sIdle.litValue())
      c.clock.step()

      /* WAIT START */
      assert (c.io.ctestState.peek.litValue() == AESState.sWaitStart.litValue())

      assert (c.io.dcplrIO.start_ready.peek.litToBoolean)
      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.dcplrIO.op_type.poke(op_type)
      c.io.dcplrIO.block_count.poke(num_block.asUInt)
      c.clock.step()

      /* AES RUN */
      assert (c.io.ctestState.peek.litValue() == AESState.sAESRun.litValue())
      // set aes start register
      assert (c.io.aesCoreIO.cs.peek.litToBoolean )
      assert (c.io.aesCoreIO.we.peek.litToBoolean )
      assert (c.io.aesCoreIO.address.peek.litValue == AESAddr.CTRL.litValue())
      assert (c.io.aesCoreIO.write_data.peek.litValue == 2)
      c.clock.step()
      c.io.aesCoreIO.read_data.poke(0.U) // aes expanding
      c.clock.step()

      /* WAIT RESULT */
      assert (c.io.ctestState.peek.litValue() == AESState.sWaitResult.litValue()) 
      c.clock.step(10) // cycles to encrypt/decrypt
      c.io.aesCoreIO.read_data.poke(1.U) // aes expand done
      c.clock.step()

      /* DATA WRITE */
      assert (c.io.ctestState.peek.litValue() == AESState.sDataWrite.litValue()) 
      assert (c.io.mtestState.peek.litValue() == MemState.sWrite.litValue()) 

      // wait for memory storing result
      c.io.dmem.req.ready.poke(true.B) // memory always ready
      while (c.io.testCounter.peek.litValue() != 4) {
        // check states
        assert(c.io.ctestState.peek.litValue() == AESState.sDataWrite.litValue())
        assert(c.io.mtestState.peek.litValue() == MemState.sWrite.litValue())

        // check aes address is correct
        assert (c.io.aesCoreIO.cs.peek.litToBoolean)
        assert (!c.io.aesCoreIO.we.peek.litToBoolean)
        assert (c.io.aesCoreIO.address.peek.litValue() == AESAddr.RESULT.litValue() + counter)
        c.clock.step()

        // check states
        assert(c.io.ctestState.peek.litValue() == AESState.sDataWrite.litValue())
        assert(c.io.mtestState.peek.litValue() == MemState.sWriteAddr.litValue())
        // check memory address is correct
        c.io.aesCoreIO.read_data.poke(counter.asUInt)
        assert (c.io.dmem.req.valid.peek.litToBoolean) 
        assert (c.io.dmem.req.bits.addr.peek.litValue() == (dest_addr + 4 * counter))
        assert (c.io.dmem.req.bits.data.peek.litValue() == counter)
        counter = counter + 1
        c.clock.step()
      }
      c.clock.step(2) // transit from memWrite to memIdle
      assert(c.io.mtestState.peek.litValue() == MemState.sIdle.litValue())
      c.clock.step()

      while (c.io.testRemain.peek.litValue() != 0) {
        counter = 0
        src_addr = src_addr + 16
        dest_addr = dest_addr + 16
        /* DATA SETUP */
        assert (c.io.ctestState.peek.litValue() == AESState.sDataSetup.litValue())
        
        // wait for memory loading text
        c.io.dmem.req.ready.poke(true.B) // memory always ready
        c.io.dmem.resp.valid.poke(true.B) // memory resp always valid
        while (c.io.testCounter.peek.litValue() != 4) {
          assert (c.io.dmem.req.valid.peek.litToBoolean)
          // check memory address is correct
          assert (c.io.dmem.req.bits.addr.peek.litValue() == (src_addr + 4 * counter))
          c.clock.step()
          // check aes address is correct
          assert(c.io.ctestState.peek.litValue() == AESState.sDataSetup.litValue())
          assert (c.io.aesCoreIO.cs.peek.litToBoolean)
          assert (c.io.aesCoreIO.we.peek.litToBoolean)
          assert (c.io.aesCoreIO.address.peek.litValue() == AESAddr.TEXT.litValue() + counter)
          counter = counter + 1
          c.clock.step()
        }
        counter = 0
        c.io.dmem.req.ready.poke(false.B) // memory always ready
        c.io.dmem.resp.valid.poke(false.B) // memory resp always valid
        c.clock.step()
        assert (c.io.mtestState.peek.litValue() == MemState.sIdle.litValue())
        c.clock.step()

        /* AES RUN */
        assert (c.io.ctestState.peek.litValue() == AESState.sAESRun.litValue())
        // set aes start register
        assert (c.io.aesCoreIO.cs.peek.litToBoolean )
        assert (c.io.aesCoreIO.we.peek.litToBoolean )
        assert (c.io.aesCoreIO.address.peek.litValue == AESAddr.CTRL.litValue())
        assert (c.io.aesCoreIO.write_data.peek.litValue == 2)
        c.clock.step()
        c.io.aesCoreIO.read_data.poke(0.U) 
        c.clock.step()

        /* WAIT RESULT */
        assert (c.io.ctestState.peek.litValue() == AESState.sWaitResult.litValue()) 
        c.clock.step(10) // cycles to encrypt/decrypt
        c.io.aesCoreIO.read_data.poke(1.U) 
        c.clock.step()

        /* DATA WRITE */
        assert (c.io.ctestState.peek.litValue() == AESState.sDataWrite.litValue()) 
        assert (c.io.mtestState.peek.litValue() == MemState.sWrite.litValue()) 

        // wait for memory storing result
        c.io.dmem.req.ready.poke(true.B) // memory always ready
        while (c.io.testCounter.peek.litValue() != 4) {
          // check states
          assert(c.io.ctestState.peek.litValue() == AESState.sDataWrite.litValue())
          assert(c.io.mtestState.peek.litValue() == MemState.sWrite.litValue())

          // check aes address is correct
          assert (c.io.aesCoreIO.cs.peek.litToBoolean)
          assert (!c.io.aesCoreIO.we.peek.litToBoolean)
          assert (c.io.aesCoreIO.address.peek.litValue() == AESAddr.RESULT.litValue() + counter)
          c.clock.step()

          // check states
          assert(c.io.ctestState.peek.litValue() == AESState.sDataWrite.litValue())
          assert(c.io.mtestState.peek.litValue() == MemState.sWriteAddr.litValue())
          // check memory address is correct
          c.io.aesCoreIO.read_data.poke(counter.asUInt)
          assert (c.io.dmem.req.valid.peek.litToBoolean) 
          assert (c.io.dmem.req.bits.addr.peek.litValue() == (dest_addr + 4 * counter))
          assert (c.io.dmem.req.bits.data.peek.litValue() == counter)
          counter = counter + 1
          c.clock.step()
        }
        c.clock.step(2) // transit from memWrite to memIdle
        assert(c.io.mtestState.peek.litValue() == MemState.sIdle.litValue())
        c.clock.step()
      }
      assert (c.io.ctestState.peek.litValue() == AESState.sIdle.litValue())
    }
  }
}
