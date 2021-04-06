package aes

import AESTestUtils._
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.{RoCCCommand, RoCCResponse}
import verif._

import scala.util.Random

/*
Tests that verify basic functionality of the decoupler.
 */

class DcplrSanityTest extends AnyFlatSpec with ChiselScalatestTester {
  implicit val p: Parameters = VerifTestUtils.getVerifParameters(xLen = 32) // Testing for our 32b RISC-V chip


  it should "elaborate the Decoupler" in {
    test(new RoCCDecoupler()) { _ =>
      assert(true)
    }
  }

  it should "sanity check decoupler output key" in {
    test(new RoCCDecoupler()) { c =>
      // Initializing RoCCCommand driver
      val driver = new DecoupledDriverMaster[RoCCCommand](c.clock, c.io.rocc_cmd)
      val txProto = new DecoupledTX(new RoCCCommand())

      // Initialize random generator (for cycle count)
      val r = new Random

      // Initialize signals
      c.io.reset.poke(false.B)
      c.io.rocc_excp.poke(false.B)
      c.io.ctrlIO.interrupt.poke(false.B)
      c.io.ctrlIO.busy.poke(false.B)
      c.io.ctrlIO.excp_ready.poke(false.B)
      c.io.ctrlIO.key_ready.poke(false.B)
      c.io.ctrlIO.addr_ready.poke(false.B)
      c.io.ctrlIO.start_ready.poke(false.B)
      c.clock.step()

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      // Checking when key ready is low
      var addr = r.nextInt(2 << 32)
      driver.push(txProto.tx(keyLoad128(addr)))
      c.clock.step(2) // 1 cycle for driver, 1 cycle for decoupler (total 2)

      assert(c.io.ctrlIO.key_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.key_addr.peek.litValue() == addr)
      assert(c.io.ctrlIO.key_size.peek.litValue() == 0)
      c.clock.step(r.nextInt(100))

      assert(c.io.ctrlIO.key_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.key_addr.peek.litValue() == addr)
      assert(c.io.ctrlIO.key_size.peek.litValue() == 0)
      // Asserting key ready. Expect valid to go low next cycle
      c.io.ctrlIO.key_ready.poke(true.B)
      c.clock.step()

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      // Valid should still be low
      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      // NOTE: KEY READY SHOULD STILL BE HIGH
      // Checking behavior when when key ready is already high
      addr = r.nextInt(2 << 32)
      driver.push(txProto.tx(keyLoad128(addr)))
      c.clock.step(2)

      // Valid should be high for only ONE cycle
      assert(c.io.ctrlIO.key_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.key_addr.peek.litValue() == addr)
      assert(c.io.ctrlIO.key_size.peek.litValue() == 0)
      c.clock.step()

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)

      c.io.ctrlIO.key_ready.poke(false.B)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      // Repeating for second key load (256)
      addr = r.nextInt(2 << 32)
      driver.push(txProto.tx(keyLoad256(addr)))
      c.clock.step(2)

      assert(c.io.ctrlIO.key_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.key_addr.peek.litValue() == addr)
      assert(c.io.ctrlIO.key_size.peek.litValue() == 1)
      c.clock.step(r.nextInt(100))

      assert(c.io.ctrlIO.key_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.key_addr.peek.litValue() == addr)
      assert(c.io.ctrlIO.key_size.peek.litValue() == 1)
      // Asserting key ready. Expect valid to go low next cycle
      c.io.ctrlIO.key_ready.poke(true.B)
      c.clock.step()

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      // Valid should still be low
      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      // NOTE: KEY READY SHOULD STILL BE HIGH
      // Checking behavior when when key ready is already high
      addr = r.nextInt(2 << 32)
      driver.push(txProto.tx(keyLoad256(addr)))
      c.clock.step(2)

      // Valid should be high for only ONE cycle
      assert(c.io.ctrlIO.key_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.key_addr.peek.litValue() == addr)
      assert(c.io.ctrlIO.key_size.peek.litValue() == 1)
      c.clock.step()

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)

      c.io.ctrlIO.key_ready.poke(false.B)
      c.clock.step(r.nextInt(100))
    }
  }

  it should "sanity check decoupler output addr" in {
    test(new RoCCDecoupler()) { c =>
      // Initializing RoCCCommand driver
      val driver = new DecoupledDriverMaster[RoCCCommand](c.clock, c.io.rocc_cmd)
      val txProto = new DecoupledTX(new RoCCCommand())

      // Initialize random generator (for cycle count)
      val r = new Random

      // Initialize signals
      c.io.reset.poke(false.B)
      c.io.rocc_excp.poke(false.B)
      c.io.ctrlIO.interrupt.poke(false.B)
      c.io.ctrlIO.busy.poke(false.B)
      c.io.ctrlIO.excp_ready.poke(false.B)
      c.io.ctrlIO.key_ready.poke(false.B)
      c.io.ctrlIO.addr_ready.poke(false.B)
      c.io.ctrlIO.start_ready.poke(false.B)
      c.clock.step()


      assert(!c.io.ctrlIO.addr_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.addr_valid.peek.litToBoolean)
      // Checking when key ready is low
      var src = r.nextInt(2 << 32)
      var dest = r.nextInt(2 << 32)
      driver.push(txProto.tx(addrLoad(src, dest)))
      c.clock.step(2) // 1 cycle for driver, 1 cycle for decoupler (total 2)

      assert(c.io.ctrlIO.addr_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.src_addr.peek.litValue() == src)
      assert(c.io.ctrlIO.dest_addr.peek.litValue() == dest)
      c.clock.step(r.nextInt(100))

      assert(c.io.ctrlIO.addr_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.src_addr.peek.litValue() == src)
      assert(c.io.ctrlIO.dest_addr.peek.litValue() == dest)
      assert(c.io.ctrlIO.key_size.peek.litValue() == 0)
      // Asserting key ready. Expect valid to go low next cycle
      c.io.ctrlIO.addr_ready.poke(true.B)
      c.clock.step()

      assert(!c.io.ctrlIO.addr_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      // Valid should still be low
      assert(!c.io.ctrlIO.addr_valid.peek.litToBoolean)
      // NOTE: ADDR READY SHOULD STILL BE HIGH
      // Checking behavior when when key ready is already high
      src = r.nextInt(2 << 32)
      dest = r.nextInt(2 << 32)
      driver.push(txProto.tx(addrLoad(src, dest)))
      c.clock.step(2)

      // Valid should be high for only ONE cycle
      assert(c.io.ctrlIO.addr_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.src_addr.peek.litValue() == src)
      assert(c.io.ctrlIO.dest_addr.peek.litValue() == dest)
      assert(c.io.ctrlIO.key_size.peek.litValue() == 0)
      c.clock.step()

      assert(!c.io.ctrlIO.addr_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.addr_valid.peek.litToBoolean)

      c.io.ctrlIO.addr_ready.poke(false.B)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.addr_valid.peek.litToBoolean)
    }
  }

  it should "sanity check decoupler output enc/dec" in {
    test(new RoCCDecoupler()) { c =>
      // Initializing RoCCCommand driver
      val driver = new DecoupledDriverMaster[RoCCCommand](c.clock, c.io.rocc_cmd)
      val txProto = new DecoupledTX(new RoCCCommand())

      // Initialize random generator (for cycle count)
      val r = new Random

      // Initialize signals
      c.io.reset.poke(false.B)
      c.io.rocc_excp.poke(false.B)
      c.io.ctrlIO.interrupt.poke(false.B)
      c.io.ctrlIO.busy.poke(false.B)
      c.io.ctrlIO.excp_ready.poke(false.B)
      c.io.ctrlIO.key_ready.poke(false.B)
      c.io.ctrlIO.addr_ready.poke(false.B)
      c.io.ctrlIO.start_ready.poke(false.B)
      c.clock.step()

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      // Checking when key ready is low
      var blocks = r.nextInt(2 << 32)
      driver.push(txProto.tx(encBlock(blocks, interrupt_en = 0)))
      c.clock.step(2) // 1 cycle for driver, 1 cycle for decoupler (total 2)

      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.block_count.peek.litValue() == blocks)
      assert(c.io.ctrlIO.op_type.peek.litValue() == 1)
      c.clock.step(r.nextInt(100))

      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.block_count.peek.litValue() == blocks)
      assert(c.io.ctrlIO.op_type.peek.litValue() == 1)
      // Asserting key ready. Expect valid to go low next cycle
      c.io.ctrlIO.start_ready.poke(true.B)
      c.clock.step()

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      // Valid should still be low
      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      // NOTE: KEY READY SHOULD STILL BE HIGH
      // Checking behavior when when key ready is already high
      blocks = r.nextInt(2 << 32)
      driver.push(txProto.tx(encBlock(blocks, interrupt_en = 0)))
      c.clock.step(2)

      // Valid should be high for only ONE cycle
      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.block_count.peek.litValue() == blocks)
      assert(c.io.ctrlIO.op_type.peek.litValue() == 1)
      c.clock.step()

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)

      c.io.ctrlIO.start_ready.poke(false.B)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      // Repeating for decryption
      blocks = r.nextInt(2 << 32)
      driver.push(txProto.tx(decBlock(blocks, interrupt_en = 0)))
      c.clock.step(2)

      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.block_count.peek.litValue() == blocks)
      assert(c.io.ctrlIO.op_type.peek.litValue() == 0)
      c.clock.step(r.nextInt(100))

      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.block_count.peek.litValue() == blocks)
      assert(c.io.ctrlIO.op_type.peek.litValue() == 0)
      // Asserting key ready. Expect valid to go low next cycle
      c.io.ctrlIO.start_ready.poke(true.B)
      c.clock.step()

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      // Valid should still be low
      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      // NOTE: KEY READY SHOULD STILL BE HIGH
      // Checking behavior when when key ready is already high
      blocks = r.nextInt(2 << 32)
      driver.push(txProto.tx(decBlock(blocks, interrupt_en = 0)))
      c.clock.step(2)

      // Valid should be high for only ONE cycle
      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.block_count.peek.litValue() == blocks)
      assert(c.io.ctrlIO.op_type.peek.litValue() == 0)
      c.clock.step()

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)

      c.io.ctrlIO.start_ready.poke(false.B)
      c.clock.step(r.nextInt(100))
    }
  }

  /* From decoupler perspective, busy should be high when a complete enc/dec operation has been received and not yet completed
     busy = start_valid | controller_busy
     Will need integration test to completely verify this
   */
  it should "sanity check decoupler busy and status query" in {
    test(new RoCCDecoupler()) { c =>
      // Initializing RoCCCommand driver
      val driver = new DecoupledDriverMaster[RoCCCommand](c.clock, c.io.rocc_cmd)
      val txProto = new DecoupledTX(new RoCCCommand())
      val monitor = new DecoupledMonitor[RoCCResponse](c.clock, c.io.rocc_resp)
      val receiver = new DecoupledDriverSlave[RoCCResponse](c.clock, c.io.rocc_resp, 0)

      // Initialize random generator (for cycle count)
      val r = new Random

      // Initialize signals
      c.io.reset.poke(false.B)
      c.io.rocc_excp.poke(false.B)
      c.io.ctrlIO.interrupt.poke(false.B)
      c.io.ctrlIO.busy.poke(false.B)
      c.io.ctrlIO.excp_ready.poke(false.B)
      c.io.ctrlIO.key_ready.poke(false.B)
      c.io.ctrlIO.addr_ready.poke(false.B)
      c.io.ctrlIO.start_ready.poke(false.B)
      c.clock.step()

      assert(!c.io.rocc_busy.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.rocc_busy.peek.litToBoolean)
      driver.push(txProto.tx(keyLoad128(r.nextInt(2 << 32))))
      c.clock.step(2)

      assert(!c.io.rocc_busy.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.rocc_busy.peek.litToBoolean)
      driver.push(txProto.tx(addrLoad(r.nextInt(2 << 32), r.nextInt(2 << 32))))
      c.clock.step(2)

      assert(!c.io.rocc_busy.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.rocc_busy.peek.litToBoolean)
      driver.push(txProto.tx(encBlock(r.nextInt(2 << 32), interrupt_en = 0)))
      c.clock.step(2)

      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.rocc_busy.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(c.io.rocc_busy.peek.litToBoolean)
      c.io.ctrlIO.start_ready.poke(true.B)
      c.clock.step()

      assert(!c.io.rocc_busy.peek.litToBoolean)
      c.io.ctrlIO.start_ready.poke(false.B)
      c.clock.step(r.nextInt(100))

      // Query busy status (not busy)
      var rd = r.nextInt(32)
      driver.push(txProto.tx(getStatus(rd)))
      c.clock.step(2)

      // Should be valid for one cycle
      assert(c.io.rocc_resp.valid.peek.litToBoolean)
      c.clock.step()

      var txns = monitor.monitoredTransactions
      assert(txns.size == 1)
      assert(!c.io.rocc_resp.valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      txns = monitor.monitoredTransactions
      assert(txns.size == 1) // Still only one response
      var resp = txns.head.data
      assert(resp.rd.litValue() == rd)
      assert(resp.data.litValue() == 0) // Currently busy
      monitor.clearMonitoredTransactions() // Clear transactions

      assert(!c.io.rocc_busy.peek.litToBoolean)
      driver.push(txProto.tx(decBlock(r.nextInt(2 << 32), interrupt_en = 0)))
      c.clock.step(2)

      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.rocc_busy.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      // Query busy status (busy)
      rd = r.nextInt(32)
      driver.push(txProto.tx(getStatus(rd)))
      c.clock.step(2)

      // Should be valid for one cycle
      assert(c.io.rocc_resp.valid.peek.litToBoolean)
      c.clock.step()

      txns = monitor.monitoredTransactions
      assert(txns.size == 1)
      assert(!c.io.rocc_resp.valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      txns = monitor.monitoredTransactions
      assert(txns.size == 1) // Still only one response
      resp = txns.head.data
      assert(resp.rd.litValue() == rd)
      assert(resp.data.litValue() == 1) // Currently busy
      monitor.clearMonitoredTransactions() // Clear transactions

      assert(c.io.rocc_busy.peek.litToBoolean)
      c.io.ctrlIO.start_ready.poke(true.B)
      c.clock.step()

      assert(!c.io.rocc_busy.peek.litToBoolean)
      c.io.ctrlIO.start_ready.poke(false.B)
      c.clock.step(r.nextInt(100))

      assert(!c.io.rocc_busy.peek.litToBoolean)
      c.io.ctrlIO.busy.poke(true.B)
      c.clock.step()

      assert(c.io.rocc_busy.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(c.io.rocc_busy.peek.litToBoolean)
      c.io.ctrlIO.busy.poke(false.B)
      c.clock.step()

      assert(!c.io.rocc_busy.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.rocc_busy.peek.litToBoolean)
    }
  }

  it should "test decoupler reset and exception when receiving instruction" in {
    test(new RoCCDecoupler()) { c =>
      // Initializing RoCCCommand driver
      val driver = new DecoupledDriverMaster[RoCCCommand](c.clock, c.io.rocc_cmd)
      val txProto = new DecoupledTX(new RoCCCommand())
      val monitor = new DecoupledMonitor[RoCCResponse](c.clock, c.io.rocc_resp)
      val receiver = new DecoupledDriverSlave[RoCCResponse](c.clock, c.io.rocc_resp, 0)

      // Initialize random generator (for cycle count)
      val r = new Random

      // Initialize signals
      c.io.reset.poke(false.B)
      c.io.rocc_excp.poke(false.B)
      c.io.ctrlIO.interrupt.poke(false.B)
      c.io.ctrlIO.busy.poke(false.B)
      c.io.ctrlIO.excp_ready.poke(false.B)
      c.io.ctrlIO.key_ready.poke(false.B)
      c.io.ctrlIO.addr_ready.poke(false.B)
      c.io.ctrlIO.start_ready.poke(false.B)
      c.clock.step()

      c.io.reset.poke(true.B) // First testing reset
      c.clock.step()

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      driver.push(txProto.tx(keyLoad128(r.nextInt(2 << 32))))
      c.clock.step(2)

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      driver.push(txProto.tx(keyLoad256(r.nextInt(2 << 32))))
      c.clock.step(2)

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      driver.push(txProto.tx(addrLoad(r.nextInt(2 << 32), r.nextInt(2 << 32))))
      c.clock.step(2)

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      driver.push(txProto.tx(encBlock(r.nextInt(2 << 32), interrupt_en = 0)))
      c.clock.step(2)

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      driver.push(txProto.tx(decBlock(r.nextInt(2 << 32), interrupt_en = 0)))
      c.clock.step(2)

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      driver.push(txProto.tx(getStatus(r.nextInt(32))))
      c.clock.step(2)

      assert(monitor.monitoredTransactions.isEmpty)
      c.clock.step(2)

      assert(monitor.monitoredTransactions.isEmpty)
      c.clock.step(r.nextInt(100))

      c.io.reset.poke(false.B) // Testing exception
      c.io.rocc_excp.poke(true.B)
      c.clock.step()

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      driver.push(txProto.tx(keyLoad128(r.nextInt(2 << 32))))
      c.clock.step(2)

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      driver.push(txProto.tx(keyLoad256(r.nextInt(2 << 32))))
      c.clock.step(2)

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      driver.push(txProto.tx(addrLoad(r.nextInt(2 << 32), r.nextInt(2 << 32))))
      c.clock.step(2)

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      driver.push(txProto.tx(encBlock(r.nextInt(2 << 32), interrupt_en = 0)))
      c.clock.step(2)

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      driver.push(txProto.tx(decBlock(r.nextInt(2 << 32), interrupt_en = 0)))
      c.clock.step(2)

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      driver.push(txProto.tx(getStatus(r.nextInt(32))))
      c.clock.step(2)

      assert(monitor.monitoredTransactions.isEmpty)
      c.clock.step(2)

      assert(monitor.monitoredTransactions.isEmpty)
    }
  }

  it should "test decoupler reset and exception when idle" in {
    test(new RoCCDecoupler()) { c =>
      // Initializing RoCCCommand driver
      val driver = new DecoupledDriverMaster[RoCCCommand](c.clock, c.io.rocc_cmd)
      val txProto = new DecoupledTX(new RoCCCommand())

      // Initialize random generator (for cycle count)
      val r = new Random

      // Inputs
      val inputs = Seq(
        txProto.tx(keyLoad128(r.nextInt(2 << 32))),
        txProto.tx(addrLoad(r.nextInt(2 << 32), r.nextInt(2 << 32))),
        txProto.tx(encBlock(r.nextInt(2 << 32), interrupt_en = 0)),
      )

      // Initialize signals
      c.io.reset.poke(false.B)
      c.io.rocc_excp.poke(false.B)
      c.io.ctrlIO.interrupt.poke(false.B)
      c.io.ctrlIO.busy.poke(false.B)
      c.io.ctrlIO.excp_ready.poke(false.B)
      c.io.ctrlIO.key_ready.poke(false.B)
      c.io.ctrlIO.addr_ready.poke(false.B)
      c.io.ctrlIO.start_ready.poke(false.B)
      c.clock.step()

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      assert(!c.io.ctrlIO.addr_valid.peek.litToBoolean)
      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      driver.push(inputs)
      c.clock.step(10)

      assert(c.io.ctrlIO.key_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.addr_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      c.io.reset.poke(true.B)
      c.clock.step()

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      assert(!c.io.ctrlIO.addr_valid.peek.litToBoolean)
      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(10))

      c.io.reset.poke(false.B)
      c.clock.step()

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      assert(!c.io.ctrlIO.addr_valid.peek.litToBoolean)
      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      // Testing exceptions
      driver.push(inputs)
      c.clock.step(10)

      assert(c.io.ctrlIO.key_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.addr_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      c.io.rocc_excp.poke(true.B)
      c.clock.step()

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      assert(!c.io.ctrlIO.addr_valid.peek.litToBoolean)
      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.excp_valid.peek.litToBoolean)

      //Testing exception acceptance
      c.clock.step(r.nextInt(5))
      c.io.ctrlIO.excp_ready.poke(true.B)
      c.clock.step()
      c.io.ctrlIO.excp_ready.poke(false.B)
      assert(!c.io.ctrlIO.excp_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(10))

      c.io.rocc_excp.poke(false.B)
      c.clock.step()

      assert(!c.io.ctrlIO.key_valid.peek.litToBoolean)
      assert(!c.io.ctrlIO.addr_valid.peek.litToBoolean)
      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
    }
  }

  it should "sanity check decoupler output enc/dec with interrupt enable signal" in {
    test(new RoCCDecoupler()) { c =>
      // Initializing RoCCCommand driver
      val driver = new DecoupledDriverMaster[RoCCCommand](c.clock, c.io.rocc_cmd)
      val txProto = new DecoupledTX(new RoCCCommand())

      // Initialize random generator (for cycle count)
      val r = new Random

      // Initialize signals
      c.io.reset.poke(false.B)
      c.io.rocc_excp.poke(false.B)
      c.io.ctrlIO.interrupt.poke(false.B)
      c.io.ctrlIO.busy.poke(false.B)
      c.io.ctrlIO.excp_ready.poke(false.B)
      c.io.ctrlIO.key_ready.poke(false.B)
      c.io.ctrlIO.addr_ready.poke(false.B)
      c.io.ctrlIO.start_ready.poke(false.B)
      c.clock.step()

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      // Checking when key ready is low
      var blocks = r.nextInt(2 << 32)
      // enable interrupt signals
      driver.push(txProto.tx(encBlock(blocks, 1)))
      c.clock.step(2) // 1 cycle for driver, 1 cycle for decoupler (total 2)

      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.block_count.peek.litValue() == blocks)
      assert(c.io.ctrlIO.op_type.peek.litValue() == 1)
      assert(c.io.ctrlIO.intrpt_en.peek.litValue() == 1)
      c.clock.step(r.nextInt(100))

      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.block_count.peek.litValue() == blocks)
      assert(c.io.ctrlIO.op_type.peek.litValue() == 1)
      assert(c.io.ctrlIO.intrpt_en.peek.litValue() == 1)
      // Asserting key ready. Expect valid to go low next cycle
      c.io.ctrlIO.start_ready.poke(true.B)
      c.clock.step()

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      // Valid should still be low
      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      // NOTE: KEY READY SHOULD STILL BE HIGH
      // Checking behavior when when key ready is already high
      blocks = r.nextInt(2 << 32)
      // disable interrupt signal
      driver.push(txProto.tx(encBlock(blocks, 0)))
      c.clock.step(2)

      // Valid should be high for only ONE cycle
      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.block_count.peek.litValue() == blocks)
      assert(c.io.ctrlIO.op_type.peek.litValue() == 1)
      assert(c.io.ctrlIO.intrpt_en.peek.litValue() == 0)
      c.clock.step()

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)

      c.io.ctrlIO.start_ready.poke(false.B)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      // Repeating for decryption
      blocks = r.nextInt(2 << 32)
      // enable interrupt signal
      driver.push(txProto.tx(decBlock(blocks, 1)))
      c.clock.step(2)

      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.block_count.peek.litValue() == blocks)
      assert(c.io.ctrlIO.op_type.peek.litValue() == 0)
      assert(c.io.ctrlIO.intrpt_en.peek.litValue() == 1)
      c.clock.step(r.nextInt(100))

      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.block_count.peek.litValue() == blocks)
      assert(c.io.ctrlIO.op_type.peek.litValue() == 0)
      assert(c.io.ctrlIO.intrpt_en.peek.litValue() == 1)
      // Asserting key ready. Expect valid to go low next cycle
      c.io.ctrlIO.start_ready.poke(true.B)
      c.clock.step()

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      // Valid should still be low
      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      // NOTE: KEY READY SHOULD STILL BE HIGH
      // Checking behavior when when key ready is already high
      blocks = r.nextInt(2 << 32)
      // disable interrupt signal
      driver.push(txProto.tx(decBlock(blocks, 0)))
      c.clock.step(2)

      // Valid should be high for only ONE cycle
      assert(c.io.ctrlIO.start_valid.peek.litToBoolean)
      assert(c.io.ctrlIO.block_count.peek.litValue() == blocks)
      assert(c.io.ctrlIO.op_type.peek.litValue() == 0)
      assert(c.io.ctrlIO.intrpt_en.peek.litValue() == 0)
      c.clock.step()

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)
      c.clock.step(r.nextInt(100))

      assert(!c.io.ctrlIO.start_valid.peek.litToBoolean)

      c.io.ctrlIO.start_ready.poke(false.B)
      c.clock.step(r.nextInt(100))
    }
  }
}
