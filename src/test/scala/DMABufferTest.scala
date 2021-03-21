package aes

import chisel3._
import chisel3.util.DecoupledIO
import chiseltest._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation
import ee290cdma.EE290CDMAWriterReq
import org.scalatest.flatspec.AnyFlatSpec
import verif._

class DMABufferTest extends AnyFlatSpec with ChiselScalatestTester {

  // ---------------------------- DMA Input Buffer Tests ----------------------------
  // Testing various beatByte sizes centered at 16 (128bits) for complete block write

  it should "Test DMAInputBuffer when beatBytes == 16" in {
    val beatBytes = 16
    test(new DMAInputBuffer(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      val addrDriver = new DecoupledDriverMaster(c.clock, c.io.baseAddr)
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dataIn)
      val outDriver = new DecoupledDriverSlave[EE290CDMAWriterReq](c.clock, c.io.dmaOutput, 0)
      val outMonitor = new DecoupledMonitor[EE290CDMAWriterReq](c.clock, c.io.dmaOutput)

      val addrInputs = Seq.fill(25)(BigInt(32, scala.util.Random))
      val resultDataBlocks = Seq.fill(25)(BigInt(128, scala.util.Random))
      val mask = BigInt("1" * 32, 2) // For masking into 32bit blocks

      // Driving inputs
      for (i <- addrInputs.indices) {
        addrDriver.push(new DecoupledTX(UInt(32.W)).tx(addrInputs(i).U))
        val dataBlock = resultDataBlocks(i)
        val dataInputs = Seq(dataBlock & mask, (dataBlock >> 32) & mask, (dataBlock >> 64) & mask, dataBlock >> 96)
        inDriver.push(dataInputs.map(x => new DecoupledTX(UInt(32.W)).tx(x.U)))
        c.clock.step(15)
      }

      assert(outMonitor.monitoredTransactions.nonEmpty)
      assert(outMonitor.monitoredTransactions.size == resultDataBlocks.size)
      outMonitor.monitoredTransactions
        .map(x => x.data.addr.litValue())
        .zip(addrInputs)
        .foreach {case (o, e) => assert(o == e)}
      outMonitor.monitoredTransactions
        .map(x => x.data.data.litValue())
        .zip(resultDataBlocks)
        .foreach {case (o, e) => assert(o == e)}
      outMonitor.monitoredTransactions
        .map(x => assert(x.data.totalBytes.litValue() == beatBytes))
    }
  }

  it should "Test DMAInputBuffer when beatBytes == 8 (less than 16)" in {
    val beatBytes = 8
    test(new DMAInputBuffer(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      val addrDriver = new DecoupledDriverMaster(c.clock, c.io.baseAddr)
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dataIn)
      val outDriver = new DecoupledDriverSlave[EE290CDMAWriterReq](c.clock, c.io.dmaOutput, 0)
      val outMonitor = new DecoupledMonitor[EE290CDMAWriterReq](c.clock, c.io.dmaOutput)

      val addrInputs = Seq.fill(25)(BigInt(32, scala.util.Random))
      val dataBlocks = Seq.fill(25)(BigInt(128, scala.util.Random))

      // Expected results
      var resultAddrInputs = Seq[BigInt]()
      for (i <- addrInputs) {
        resultAddrInputs = resultAddrInputs ++ Seq(i, i + 8)
      }
      var resultDataBlocks = Seq[BigInt]()
      for (i <- dataBlocks) {
        resultDataBlocks = resultDataBlocks ++ Seq(i & BigInt("1" * 64, 2), i >> 64)
      }
      val mask = BigInt("1" * 32, 2) // For masking into 32bit blocks

      // Driving inputs
      for (i <- addrInputs.indices) {
        addrDriver.push(new DecoupledTX(UInt(32.W)).tx(addrInputs(i).U))
        val dataBlock = dataBlocks(i)
        val dataInputs = Seq(dataBlock & mask, (dataBlock >> 32) & mask, (dataBlock >> 64) & mask, dataBlock >> 96)
        inDriver.push(dataInputs.map(x => new DecoupledTX(UInt(32.W)).tx(x.U)))
        c.clock.step(15)
      }

      assert(outMonitor.monitoredTransactions.nonEmpty)

      assert(outMonitor.monitoredTransactions.size == resultAddrInputs.size &&
        resultAddrInputs.size == resultDataBlocks.size)
      outMonitor.monitoredTransactions
        .map(x => x.data.addr.litValue())
        .zip(resultAddrInputs)
        .foreach {case (o, e) => assert(o == e)}

      outMonitor.monitoredTransactions
        .map(x => x.data.data.litValue())
        .zip(resultDataBlocks)
        .foreach {case (o, e) => assert(o == e)}

      outMonitor.monitoredTransactions
        .map(x => assert(x.data.totalBytes.litValue() == beatBytes))
    }
  }

  // Testing one more case for beatBytes < 16, as this is most likely the param in the actual chip
  it should "Test DMAInputBuffer when beatBytes == 4 (less than 16)" in {
    val beatBytes = 4
    test(new DMAInputBuffer(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      val addrDriver = new DecoupledDriverMaster(c.clock, c.io.baseAddr)
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dataIn)
      val outDriver = new DecoupledDriverSlave[EE290CDMAWriterReq](c.clock, c.io.dmaOutput, 0)
      val outMonitor = new DecoupledMonitor[EE290CDMAWriterReq](c.clock, c.io.dmaOutput)

      val addrInputs = Seq.fill(25)(BigInt(32, scala.util.Random))
      val dataBlocks = Seq.fill(25)(BigInt(128, scala.util.Random))

      // Expected results
      var resultAddrInputs = Seq[BigInt]()
      for (i <- addrInputs) {
        resultAddrInputs = resultAddrInputs ++ Seq(i, i + 4, i + 8, i + 12)
      }
      val mask = BigInt("1" * 32, 2) // For masking into 32bit blocks
      var resultDataBlocks = Seq[BigInt]()
      for (i <- dataBlocks) {
        resultDataBlocks = resultDataBlocks ++ Seq(i & mask, (i >> 32) & mask, (i >> 64) & mask, i >> 96)
      }

      // Driving inputs
      for (i <- addrInputs.indices) {
        addrDriver.push(new DecoupledTX(UInt(32.W)).tx(addrInputs(i).U))
        val dataBlock = dataBlocks(i)
        val dataInputs = Seq(dataBlock & mask, (dataBlock >> 32) & mask, (dataBlock >> 64) & mask, dataBlock >> 96)
        inDriver.push(dataInputs.map(x => new DecoupledTX(UInt(32.W)).tx(x.U)))
        c.clock.step(15)
      }

      assert(outMonitor.monitoredTransactions.nonEmpty)

      assert(outMonitor.monitoredTransactions.size == resultAddrInputs.size &&
        resultAddrInputs.size == resultDataBlocks.size)
      outMonitor.monitoredTransactions
        .map(x => x.data.addr.litValue())
        .zip(resultAddrInputs)
        .foreach {case (o, e) => assert(o == e)}

      outMonitor.monitoredTransactions
        .map(x => x.data.data.litValue())
        .zip(resultDataBlocks)
        .foreach {case (o, e) => assert(o == e)}

      outMonitor.monitoredTransactions
        .map(x => assert(x.data.totalBytes.litValue() == beatBytes))
    }
  }

  it should "Test DMAInputBuffer when beatBytes == 32 (greater than 16)" in {
    val beatBytes = 32
    test(new DMAInputBuffer(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      val addrDriver = new DecoupledDriverMaster(c.clock, c.io.baseAddr)
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dataIn)
      val outDriver = new DecoupledDriverSlave[EE290CDMAWriterReq](c.clock, c.io.dmaOutput, 0)
      val outMonitor = new DecoupledMonitor[EE290CDMAWriterReq](c.clock, c.io.dmaOutput)

      val addrInputs = Seq.fill(25)(BigInt(32, scala.util.Random))
      val resultDataBlocks = Seq.fill(25)(BigInt(128, scala.util.Random))
      val mask = BigInt("1" * 32, 2) // For masking into 32bit blocks

      for (i <- addrInputs.indices) {
        addrDriver.push(new DecoupledTX(UInt(32.W)).tx(addrInputs(i).U))
        val dataBlock = resultDataBlocks(i)
        val dataInputs = Seq(dataBlock & mask, (dataBlock >> 32) & mask, (dataBlock >> 64) & mask, dataBlock >> 96)
        inDriver.push(dataInputs.map(x => new DecoupledTX(UInt(32.W)).tx(x.U)))
        c.clock.step(15)
      }

      assert(outMonitor.monitoredTransactions.nonEmpty)
      assert(outMonitor.monitoredTransactions.size == addrInputs.size &&
        addrInputs.size == resultDataBlocks.size)
      outMonitor.monitoredTransactions
        .map(x => x.data.addr.litValue())
        .zip(addrInputs)
        .foreach {case (o, e) => assert(o == e)}

      outMonitor.monitoredTransactions
        .map(x => x.data.data.litValue())
        .zip(resultDataBlocks)
        .foreach {case (o, e) => assert(o == e)}

      outMonitor.monitoredTransactions
        .map(x => assert(x.data.totalBytes.litValue() == 16)) // Max out at 16 bytes (128b)
    }
  }

  // ---------------------------- DMA Output Buffer Tests ----------------------------
  // Testing various beatByte sizes centered at 4 (32bits) or intended DMA output size

  it should "Test DMAOutputBuffer when beatBytes == 4" in {
    val beatBytes = 4
    test(new DMAOutputBuffer(beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dmaInput)
      val outDriver = new DecoupledDriverSlave(c.clock, c.io.dataOut, 0)
      val outMonitor = new DecoupledMonitor(c.clock, c.io.dataOut)

      val inputs = Seq.fill(100)(BigInt((beatBytes * 8), scala.util.Random))

      inDriver.push(inputs.map(x => new DecoupledTX(UInt((beatBytes * 8).W)).tx(x.U)))
      c.clock.step(inputs.length + 200)

      assert(outMonitor.monitoredTransactions.nonEmpty)
      assert(outMonitor.monitoredTransactions.size == inputs.size)
      outMonitor.monitoredTransactions
        .map(x => x.data.litValue())
        .zip(inputs)
        .foreach {case (o, e) => assert(o == e)}
    }
  }

  it should "Test DMAOutputBuffer when beatBytes == 2 (less than 4)" in {
    val beatBytes = 2
    test(new DMAOutputBuffer(beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dmaInput)
      val outDriver = new DecoupledDriverSlave(c.clock, c.io.dataOut, 0)
      val outMonitor = new DecoupledMonitor(c.clock, c.io.dataOut)

      val inputs = Seq.fill(100)(BigInt((beatBytes * 8), scala.util.Random))
      val results = inputs.grouped(2).map { case List(x,y) => (y << 16) + x}.toList
      println(s"Result size is ${results.size}")

      inDriver.push(inputs.map(x => new DecoupledTX(UInt((beatBytes * 8).W)).tx(x.U)))
      c.clock.step(inputs.length + 200)

      assert(outMonitor.monitoredTransactions.nonEmpty)
      assert(outMonitor.monitoredTransactions.size == results.size)
      outMonitor.monitoredTransactions
        .map(x => x.data.litValue())
        .zip(results)
        .foreach {case (o, e) => assert(o == e)}
    }
  }

  it should "Test DMAOutputBuffer when beatBytes == 8 (greater than 4)" in {
    val beatBytes = 8
    test(new DMAOutputBuffer(beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dmaInput)
      val outDriver = new DecoupledDriverSlave(c.clock, c.io.dataOut, 0)
      val outMonitor = new DecoupledMonitor(c.clock, c.io.dataOut)

      val inputs = Seq.fill(100)(BigInt((beatBytes * 8), scala.util.Random))
      var results = Seq[BigInt]()
      for (i <- inputs) {
        results = results ++ Seq(i & (BigInt("1" * 32, 2)), i >> 32)
      }
      println(s"Result size is ${results.size}")

      inDriver.push(inputs.map(x => new DecoupledTX(UInt((beatBytes * 8).W)).tx(x.U)))
      c.clock.step(inputs.length * 2 + 200)

      assert(outMonitor.monitoredTransactions.nonEmpty)
      assert(outMonitor.monitoredTransactions.size == results.size)
      outMonitor.monitoredTransactions
        .map(x => x.data.litValue())
        .zip(results)
        .foreach {case (o, e) => assert(o == e)}
    }
  }
}