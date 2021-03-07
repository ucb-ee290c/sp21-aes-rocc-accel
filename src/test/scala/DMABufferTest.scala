package aes

import chisel3._
import chisel3.util._
import chiseltest._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.{TreadleBackendAnnotation, WriteVcdAnnotation}
import ee290cdma.EE290CDMAWriterReq
import org.scalatest.flatspec.AnyFlatSpec
import verif._

class DMABufferTest extends AnyFlatSpec with ChiselScalatestTester {
  it should "Test DMAInputBuffer when beatBytes == 4" in {
    val beatBytes = 4
    test(new DMAInputBuffer(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dataIn)
      val outDriver = new DecoupledDriverSlave[EE290CDMAWriterReq](c.clock, c.io.dmaOutput, 0)
      val outMonitor = new DecoupledMonitor[EE290CDMAWriterReq](c.clock, c.io.dmaOutput)

      val addrInputs = Seq.fill(100)(BigInt(32, scala.util.Random))
      val dataInputs = Seq.fill(100)(BigInt((beatBytes * 8), scala.util.Random))
      val combInputs = addrInputs.zip(dataInputs).map(x => (x._1 << 32) + x._2)

      inDriver.push(combInputs.map(x => new DecoupledTX(UInt((32 + (beatBytes * 8)).W)).tx(x.U)))
      c.clock.step(combInputs.length + 200)

      assert(outMonitor.monitoredTransactions.nonEmpty)
      assert(outMonitor.monitoredTransactions.size == combInputs.size)
      outMonitor.monitoredTransactions
        .map(x => x.data.addr.litValue())
        .zip(addrInputs)
        .foreach {case (o, e) => assert(o == e)}
      outMonitor.monitoredTransactions
        .map(x => x.data.data.litValue())
        .zip(dataInputs)
        .foreach {case (o, e) => assert(o == e)}
      outMonitor.monitoredTransactions
        .map(x => assert(x.data.totalBytes.litValue() == beatBytes))
    }
  }

  it should "Test DMAInputBuffer when beatBytes == 2 (less than 4)" in {
    val beatBytes = 2
    test(new DMAInputBuffer(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dataIn)
      val outDriver = new DecoupledDriverSlave[EE290CDMAWriterReq](c.clock, c.io.dmaOutput, 0)
      val outMonitor = new DecoupledMonitor[EE290CDMAWriterReq](c.clock, c.io.dmaOutput)

      val addrInputs = Seq.fill(100)(BigInt(32, scala.util.Random))
      val dataInputs = Seq.fill(100)(BigInt(32, scala.util.Random))
      val combInputs = addrInputs.zip(dataInputs).map(x => (x._1 << 32) + x._2)

      var addrResults = Seq[BigInt]()
      for (i <- addrInputs) {
        addrResults = addrResults ++ Seq(i, i + 2)
      }
      var dataResults = Seq[BigInt]()
      for (i <- dataInputs) {
        dataResults = dataResults ++ Seq(i & BigInt("1" * (beatBytes * 8), 2), i >> (beatBytes * 8))
      }
      println(s"Size of results: addrResults ${addrResults.size}, dataResults ${dataResults.size}")

      inDriver.push(combInputs.map(x => new DecoupledTX(UInt((32 + (beatBytes * 8)).W)).tx(x.U)))
      c.clock.step(combInputs.length*2 + 200)

      assert(outMonitor.monitoredTransactions.nonEmpty)

      assert(outMonitor.monitoredTransactions.size == addrResults.size &&
        addrResults.size == dataResults.size)
      outMonitor.monitoredTransactions
        .map(x => x.data.addr.litValue())
        .zip(addrResults)
        .foreach {case (o, e) => assert(o == e)}

      outMonitor.monitoredTransactions
        .map(x => x.data.data.litValue())
        .zip(dataResults)
        .foreach {case (o, e) => assert(o == e)}

      outMonitor.monitoredTransactions
        .map(x => assert(x.data.totalBytes.litValue() == beatBytes))
    }
  }

  it should "Test DMAInputBuffer when beatBytes == 8 (greater than 4)" in {
    val beatBytes = 8
    test(new DMAInputBuffer(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dataIn)
      val outDriver = new DecoupledDriverSlave[EE290CDMAWriterReq](c.clock, c.io.dmaOutput, 0)
      val outMonitor = new DecoupledMonitor[EE290CDMAWriterReq](c.clock, c.io.dmaOutput)

      val addrInputs = Seq.fill(10)(BigInt(32, scala.util.Random))
      val dataInputs = Seq.fill(10)(BigInt(32, scala.util.Random))
      val combInputs = addrInputs.zip(dataInputs).map(x => (x._1 << 32) + x._2)

      inDriver.push(combInputs.map(x => new DecoupledTX(UInt((32 + (beatBytes * 8)).W)).tx(x.U)))
      c.clock.step(combInputs.length + 200)

      assert(outMonitor.monitoredTransactions.nonEmpty)

      assert(outMonitor.monitoredTransactions.size == addrInputs.size &&
        addrInputs.size == dataInputs.size)
      outMonitor.monitoredTransactions
        .map(x => x.data.addr.litValue())
        .zip(addrInputs)
        .foreach {case (o, e) => assert(o == e)}

      outMonitor.monitoredTransactions
        .map(x => x.data.data.litValue())
        .zip(dataInputs)
        .foreach {case (o, e) => assert(o == e)}

      outMonitor.monitoredTransactions
        .map(x => assert(x.data.totalBytes.litValue() == 4))
    }
  }

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