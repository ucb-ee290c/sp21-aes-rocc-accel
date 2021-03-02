package aes

import aes.AESTestUtils.hellaCacheResp
import org.scalatest._
import chisel3._
import chisel3.experimental.BundleLiterals._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.rocket.{HellaCacheReq, HellaCacheResp}
import verif._

import scala.util.Random



class ctrlSanityTest extends AnyFlatSpec with ChiselScalatestTester {
  implicit val p: Parameters = VerifTestUtils.getVerifParameters(xLen = 32) // 32-bit processor

  it should "elaborate the Controller" in {
    //.withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation))
    test(new AESController()) { c =>
      assert(true)
    }
  }

  it should "test controller state transitions" in {
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

  // To Eric: Here is an example of a test using the drivers/monitors to mimic the HellaCache Interface
  // It does not act like the true cache, but at least we can receive requests and send responses
  it should "test key memory access" in {
    test(new AESController()) { c =>
      // Initializing RoCCCommand driver, receiver (dummy), and monitor
      val driver = new verif.ValidDriver[HellaCacheResp](c.clock, c.io.dmem.resp) // Used to send responses, takes in ValidTX
      val monitor = new DecoupledMonitor[HellaCacheReq](c.clock, c.io.dmem.req) // Used to observe all transactions on this interface
      val receiver = new DecoupledDriverSlave[HellaCacheReq](c.clock, c.io.dmem.req, 0) // Acts as a dummy receiver (does nothing but hold ready high)

      val r = new Random

      // Initializing decoupler <> controller interface
      c.io.dcplrIO.excp_valid.poke(false.B)
      c.io.dcplrIO.key_valid.poke(false.B)
      c.io.dcplrIO.key_size.poke(0.U(1.W))
      c.io.dcplrIO.key_addr.poke(0.U(32.W))
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.io.dcplrIO.src_addr.poke(0.U(32.W))
      c.io.dcplrIO.dest_addr.poke(0.U(32.W))
      c.io.dcplrIO.start_valid.poke(false.B)
      c.io.dcplrIO.op_type.poke(false.B)
      c.io.dcplrIO.block_count.poke(0.U(32.W))
      c.clock.step()

      // Reset (just to be safe)
      c.io.reset.poke(true.B)
      c.clock.step(r.nextInt(10))
      c.io.reset.poke(false.B)
      c.clock.step()

      // Check state here
      // assert(c.io.state == IDLE)...

      // This is not necessary since the FSM should be in IDLE (from above assert), but it works as an example
      while (!c.io.dcplrIO.key_ready.peek.litToBoolean) {
        c.clock.step()
      }

      // Triggering key expansion
      c.io.dcplrIO.key_valid.poke(true.B)
      val op_type = r.nextInt(2)
      c.io.dcplrIO.key_size.poke(op_type.U(1.W))
      val addr = r.nextInt(2 << 32)
      c.io.dcplrIO.key_addr.poke(addr.U(32.W))
      c.clock.step()

      while (c.io.dcplrIO.key_ready.peek.litToBoolean) {
        c.clock.step()
      }
      c.io.dcplrIO.key_valid.poke(false.B)
      c.clock.step()

      // Check state here
      // assert(c.io.state == KEY...)...

      // Here we check to see if we got a response. I increment by a fixed number of cycles, but this can be changed
      c.clock.step(10)

      // 4 or 8 times depending on key size
      val times = if (op_type == 1) 8 else 4
      for (_ <- 0 until times) {
        // Check that we actually received a memory request
        assert(!monitor.monitoredTransactions.isEmpty)
        val req = monitor.monitoredTransactions.head
        monitor.clearMonitoredTransactions()
        assert(req.data.addr.litValue() == addr)
        // You should do more checks here

        // Send a response
        val resp_data = r.nextInt(2 << 32)
        val resp = hellaCacheResp(resp_data)
        driver.push(ValidTX(resp))
        c.clock.step(r.nextInt(10) + 10) // This will vary, but for now, between 10-20 cycles

        // Now you check if the data is correctly presented to the AES Core
        // TODO: To Eric, fill this out as an exercise
      }

      c.clock.step(r.nextInt(100)) // Random, you can tweak this

      // Check that there are no extra requests
      assert(monitor.monitoredTransactions.isEmpty)

      // To Eric: This is an example of a test with correct behavior. But what if something goes wrong? (e.g. data returns
      // another data response? What is the correct FSM behavior?) You should write a few other tests. I will help you along the way.
    }
  }
}