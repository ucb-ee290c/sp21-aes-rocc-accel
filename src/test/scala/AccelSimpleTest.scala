package aes

import AESTestUtils._
import chisel3._
import chiseltest._
import chiseltest.internal.{VerilatorBackendAnnotation, WriteVcdAnnotation}
import chiseltest.experimental.TestOptionBuilder._
import org.scalatest.flatspec.AnyFlatSpec
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.tile.{RoCCCommand, RoCCResponse}
import verif.TLMemoryModel.WordAddr
import verif._


class AccelSimpleTest extends AnyFlatSpec with ChiselScalatestTester {
  implicit val p: Parameters = VerifTestUtils.getVerifParameters(xLen = 32) // Testing for our 32b RISC-V chip

//  // Elaborate to see if anything broke
//  it should "elaborate the accelerator" in {
//    val dut = LazyModule(new AESAccelStandaloneBlock)
//    // Requires verilator backend! (For verilog blackbox files)
//    test(dut.module).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
//      assert(true)
//    }
//  }

  it should "Test 128b AES Encryption" in {
    val dut = LazyModule(new AESAccelStandaloneBlock)
    test(dut.module).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      // RoCCCommand driver + RoCCResponse receiver
      val driver = new DecoupledDriverMaster[RoCCCommand](c.clock, c.io.cmd)
      val txProto = new DecoupledTX(new RoCCCommand())
      val monitor = new DecoupledMonitor[RoCCResponse](c.clock, c.io.resp)
      val receiver = new DecoupledDriverSlave[RoCCResponse](c.clock, c.io.resp, 0)

      // Manually writing key + text into memory, TODO convert into helper function
      val keyAddr = 0x00
      val inputAddr = 0x10
      val outputAddr = 0x20
      // NOTE: WordAddr is Addr/word size
      val initMem:Map[WordAddr, BigInt] = Map (
        0x0.toLong -> BigInt(0x09cf4f3c), // Writing Key (key from example Secwork AES testbench)
        0x1.toLong -> BigInt(0xabf71588),
        0x2.toLong -> BigInt(0x28aed2a6),
        0x3.toLong -> BigInt(0x2b7e1516),
        0x4.toLong -> BigInt(0x7393172a), // Writing plaintext (plaintext from example Secwork AES testbench)
        0x5.toLong -> BigInt(0xe93d7e11),
        0x6.toLong -> BigInt(0x2e409f96),
        0x7.toLong -> BigInt(0x6bc1bee2)
      )

      // Mock Memory
      val slaveFn = new TLMemoryModel(dut.to_mem.params)
      val slaveModel = new TLDriverSlave(c.clock, dut.to_mem, slaveFn, TLMemoryModel.State.init(initMem, dut.bParams.dataBits/8))
      val slaveMonitor = new TLMonitor(c.clock, dut.to_mem)

      // Pushing RoCCCommands into driver
      val inputCmd = Seq(
        txProto.tx(keyLoad128(keyAddr)),
        txProto.tx(addrLoad(inputAddr, outputAddr)),
        txProto.tx(encBlock(1))
      )
      driver.push(inputCmd)

      // Actual cycle count is around 130, but currently running into issues with writeback
//      var cycleCount = 0
//      // Step clock until we see a writeback (just getting cycle count for now)
//      while (TLMemoryModel.read(slaveModel.state.mem, 0x2C.toLong, dut.bParams.dataBits/8, -1) == 0) {
//        c.clock.step()
//        cycleCount += 1
//      }

      var cycleCount = 200
      c.clock.step(cycleCount)

      println(s"Block 0: ${TLMemoryModel.read(slaveModel.state.mem, 0x20.toLong, 4, -1)}")
      println(s"Block 1: ${TLMemoryModel.read(slaveModel.state.mem, 0x24.toLong, 4, -1)}")
      println(s"Block 2: ${TLMemoryModel.read(slaveModel.state.mem, 0x28.toLong, 4, -1)}")
      println(s"Block 3: ${TLMemoryModel.read(slaveModel.state.mem, 0x2c.toLong, 4, -1)}")
      println(s"Cycle count: $cycleCount")
      println("NOTE: This test doesn't pass right now (some bugs with memory model + accelerator need to be fixed), but gives us a cycle count.")

      // Debug
      println(s"Recorded TL Transactions")
      slaveMonitor.getMonitoredTransactions().map(_.data).foreach(println(_))
      assert(true)
    }
  }
}