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


class AccelTopTest extends AnyFlatSpec with ChiselScalatestTester {
  // Hardcoded, since only one value of  beatBytes works for now... (see to-do below)
  // TODO: if beatBytes != 16, I get error `rowBits(128) != cacheDataBits(64)` from HellaCache.scala:91
  // TODO: Look into what causes rowBits = 128
  val beatBytes = 16
  implicit val p: Parameters = VerifTestUtils.getVerifParameters(xLen = 32, beatBytes = beatBytes) // Testing for our 32b RISC-V chip
  val r = new scala.util.Random

  def testAccelerator(dut: AESAccelStandaloneBlock, clock: Clock, keySize: Int, encdec: Int, rounds: Int): Boolean = {
    assert(keySize == 128 || keySize == 256 || keySize == -1, s"KeySize must be 128, 256, or -1 (random). Given: $keySize")
    assert(encdec == 0 || encdec == 1 || encdec == -1, s"ENCDEC must be 1 (encrypt), 0 (decrypt), or -1 (random). Given: $encdec")

    // RoCCCommand driver + RoCCResponse receiver
    val driver = new DecoupledDriverMaster[RoCCCommand](clock, dut.module.io.cmd)
    val txProto = new DecoupledTX(new RoCCCommand())
    val monitor = new DecoupledMonitor[RoCCResponse](clock, dut.module.io.resp)
    val receiver = new DecoupledDriverSlave[RoCCResponse](clock, dut.module.io.resp, 0)

    // Mock Memory
    val slaveFn = new TLMemoryModel(dut.to_mem.params)
    val slaveModel = new TLDriverSlave(clock, dut.to_mem, slaveFn, TLMemoryModel.State.empty())
    val slaveMonitor = new TLMonitor(clock, dut.to_mem)

    var cycleCount = 0
    var blocksProcessed = 0
    var allPass = true
    var actualKeySize = 128
    var encrypt = true

    for (i <- 0 until rounds) {
      // Output: (1: keyAddr, 2: keyData (256b post-padded), 3: srcAddr, 4: textData, 5: destAddr, 6: memState)
      val destructive = r.nextBoolean()
      if (keySize == -1) actualKeySize = if (r.nextBoolean()) 128 else 256
      else actualKeySize = keySize
      if (encdec == -1) encrypt = r.nextBoolean()
      else encrypt = encdec == 1

      val stim = genAESStim(actualKeySize, r.nextInt(10) + 1, destructive = destructive, beatBytes, r)
      slaveModel.state = stim._6

//      // Debug Printing
//      println(s"Debug key size: $actualKeySize")
//      println(s"Debug encdec: $encrypt")
//      println(s"Debug: $stim")

      var inputCmd = Seq[DecoupledTX[RoCCCommand]]()
      if (actualKeySize == 128) inputCmd = inputCmd :+ txProto.tx(keyLoad128(stim._1 + 16))
      else inputCmd = inputCmd :+ txProto.tx(keyLoad256(stim._1))
      inputCmd = inputCmd :+ txProto.tx(addrLoad(stim._3, stim._5))
      if (encrypt) inputCmd = inputCmd :+ txProto.tx(encBlock(stim._4.length))
      else inputCmd = inputCmd :+ txProto.tx(decBlock(stim._4.length))
      driver.push(inputCmd)

      // Each block takes at least 75 cycles, will auto-increment
      clock.step(75 * stim._4.length)
      cycleCount += 75 * stim._4.length
      val initData = if (destructive) stim._4.last else BigInt(0)
      while(!finishedWriting(slaveModel.state, stim._5, stim._4.length, initData, beatBytes)) {
        clock.step()
        cycleCount += 1
      }

      allPass &= checkResult(actualKeySize, stim._2, stim._4, stim._5, encrypt = encrypt, slaveModel.state, beatBytes)

      blocksProcessed += stim._4.length
    }

    println(s"====== :Performance stats: ======")
    println(s"Blocks Processed: $blocksProcessed")
    println(s"Cycles Elapsed: $cycleCount")
    println(s"Average Cycles/Block: ${cycleCount/blocksProcessed.toDouble}")
    allPass
  }

  // Basic sanity test: elaborate to see if anything structure-wise broke
  it should "elaborate the accelerator" in {
    val dut = LazyModule(new AESAccelStandaloneBlock(beatBytes))
    // Requires verilator backend! (For verilog blackbox files)
    test(dut.module).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      assert(true)
    }
  }

  it should "Test 128b AES Encryption" in {
    val dut = LazyModule(new AESAccelStandaloneBlock(beatBytes))
    test(dut.module).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      val result = testAccelerator(dut, c.clock, 128, encdec = 1, 20)
      assert(result)
    }
  }

  it should "Test 128b AES Decryption" in {
    val dut = LazyModule(new AESAccelStandaloneBlock(beatBytes))
    test(dut.module).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      val result = testAccelerator(dut, c.clock, 128, encdec = -1, 20)
      assert(result)
    }
  }

  it should "Test 256b AES Encryption" in {
    val dut = LazyModule(new AESAccelStandaloneBlock(beatBytes))
    test(dut.module).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      val result = testAccelerator(dut, c.clock, 256, encdec = 1, 20)
      assert(result)
    }
  }

  it should "Test 256b AES Decryption" in {
    val dut = LazyModule(new AESAccelStandaloneBlock(beatBytes))
    test(dut.module).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      val result = testAccelerator(dut, c.clock, 256, encdec = -1, 20)
      assert(result)
    }
  }

  it should "Test Mixed 128/256 AES Encryption/Decryption" in {
    val dut = LazyModule(new AESAccelStandaloneBlock(beatBytes))
    test(dut.module).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      val result = testAccelerator(dut, c.clock, -1, encdec = -1, 20)
      assert(result)
    }
  }
}