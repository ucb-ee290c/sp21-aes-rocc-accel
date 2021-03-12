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
import ee290cdma.{EE290CDMAWriterReq, EE290CDMAReaderReq}
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation

import scala.util.Random


class ctrlSanityTest extends AnyFlatSpec with ChiselScalatestTester {
  implicit val p: Parameters = VerifTestUtils.getVerifParameters(xLen = 32) // 32-bit processor

  it should "elaborate the Controller" in {
    //.withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation))
    test(new AESController(32, 8)) { c =>
      assert(true)
    }
  }

  /*it should "test controller state transitions" in {
    implicit val p: Parameters = VerifTestUtils.getVerifParameters()
    val key_addr = 20.U
    val key_size  = 0
    var src_addr = 40
    var dest_addr = 100
    val op_type = true.B
    val num_block = 3
    var counter = 0
  }
  // To Eric: Here is an example of a test using the drivers/monitors to mimic the HellaCache Interface
  // It does not act like the true cache, but at least we can receive requests and send responses*/
  it should "test DMA interface & State Transitions (beatBytes = 4)" in {
    val beatBytes = 4
    test(new AESController(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      // Monitor for AES signals
      val outAESWriteDataMonitor = new ValidMonitor(c.clock, c.io.testAESWriteData)
      val outAESReadDataMonitor = new ValidMonitor(c.clock, c.io.testAESReadData)
      val outAESCtrlMonitor = new ValidMonitor(c.clock, c.io.testAESCtrl)
      // Driver for dma interface
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dmem.readRespQueue)
      val outWriteReqDriver = new DecoupledDriverSlave[EE290CDMAWriterReq](c.clock, c.io.dmem.writeReq, 0)
      val outWriteReqMonitor = new DecoupledMonitor[EE290CDMAWriterReq](c.clock, c.io.dmem.writeReq)
      val outReadReqDriver = new DecoupledDriverSlave[EE290CDMAReaderReq](c.clock, c.io.dmem.readReq, 0)
      val outReadReqMonitor = new DecoupledMonitor[EE290CDMAReaderReq](c.clock, c.io.dmem.readReq)
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
      c.clock.step(r.nextInt(10) + 1)
      c.io.reset.poke(false.B)
      c.clock.step()

      // Check state here
      assert (c.io.testCState.peek.litValue() == AESState.sIdle.litValue())

      // This is not necessary since the FSM should be in IDLE (from above assert), but it works as an example
      while (!c.io.dcplrIO.key_ready.peek.litToBoolean) {
        c.clock.step()
      }

      // Triggering key expansion
      var key_size = r.nextInt(2)
      var addr = r.nextInt(1 << 32)
      c.io.dcplrIO.key_valid.poke(true.B)
      c.io.dcplrIO.key_size.poke(key_size.U(1.W))
      c.io.dcplrIO.key_addr.poke(addr.U(32.W))

      c.clock.step()
      while (c.io.dcplrIO.key_ready.peek.litToBoolean) {
        c.clock.step()
      }
      c.io.dcplrIO.key_valid.poke(false.B)
      c.clock.step()
    
      // check sending aes key exp request
      // TODO: problem of peek after poke
      /*assert (!outAESCtrlMonitor.monitoredTransactions.nonEmpty)
      assert (outAESCtrlMonitor.monitoredTransactions.size == 1)
      assert (outAESCtrlMonitor.monitoredTransactions(0).data(31, 0).litValue() == 2)
      assert (outAESCtrlMonitor.monitoredTransactions(0).data(63, 32).litValue() == AESAddr.CONFIG.litValue())
      outAESCtrlMonitor.clearMonitoredTransactions()*/
      

      // Check state here
      assert (c.io.testCState.peek.litValue() == AESState.sKeySetup.litValue())
      c.clock.step(10)

      // 4 or 8 times depending on key size
      val times = if (key_size== 1) 8 else 4
      // Check that DMA  actually received a read request
      assert(!outReadReqMonitor.monitoredTransactions.isEmpty)
      var req = outReadReqMonitor.monitoredTransactions.head
      outReadReqMonitor.clearMonitoredTransactions()
      assert(req.data.addr.litValue() == addr)
      assert(req.data.totalBytes.litValue() == times * 4)

      assert(c.io.testMState.peek.litValue() == MemState.sReadIntoAES.litValue())
      // Send responses
      var inputs = Seq.fill(times * 4 / beatBytes)(BigInt((beatBytes * 8), scala.util.Random))
      inDriver.push(inputs.map(x => new DecoupledTX(UInt((beatBytes * 8).W)).tx(x.U)))
      c.clock.step(inputs.length + 200 )

      // Now you check if the data is correctly presented to the AES Core
      assert(outAESWriteDataMonitor.monitoredTransactions.nonEmpty)
      assert(outAESWriteDataMonitor.monitoredTransactions.size == times)

      outAESWriteDataMonitor.monitoredTransactions
        .map(x => x.data)
        .zipWithIndex
        .foreach {case (o, i) => {
          assert (o(31,0).litValue() == inputs(i))
          assert (o(63,32).litValue() == AESAddr.KEY.litValue() + times - 1 - i)
        }}
      outAESWriteDataMonitor.clearMonitoredTransactions()
      c.clock.step(r.nextInt(10) + 1)

      assert (c.io.testCState.peek.litValue() == AESState.sKeyExp.litValue())
      c.io.aesCoreIO.read_data.poke(0.U)
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this

      assert (c.io.testCState.peek.litValue() == AESState.sWaitData.litValue())
      // Triggering text loading
      val src_addr = r.nextInt(1 << 32)
      val dest_addr = r.nextInt(1 << 32)
      c.io.dcplrIO.addr_valid.poke(true.B)
      c.io.dcplrIO.src_addr.poke(src_addr.U(32.W))
      c.io.dcplrIO.dest_addr.poke(dest_addr.U(32.W))

      c.clock.step()
      while (c.io.dcplrIO.addr_ready.peek.litToBoolean) {
        c.clock.step()
      }
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.clock.step()

      assert (c.io.testCState.peek.litValue() == AESState.sDataSetup.litValue())
      c.clock.step(10)

      assert(!outReadReqMonitor.monitoredTransactions.isEmpty)
      req = outReadReqMonitor.monitoredTransactions.head
      outReadReqMonitor.clearMonitoredTransactions()
      assert(req.data.addr.litValue() == src_addr)
      assert(req.data.totalBytes.litValue() == 4 * 4)

      assert(c.io.testMState.peek.litValue() == MemState.sReadIntoAES.litValue())
      // Send responses
      inputs = Seq.fill(4 * 4 / beatBytes)(BigInt((beatBytes * 8), scala.util.Random))
      inDriver.push(inputs.map(x => new DecoupledTX(UInt((beatBytes * 8).W)).tx(x.U)))
      c.clock.step(inputs.length + 200 )

      // Now you check if the data is correctly presented to the AES Core
      assert(outAESWriteDataMonitor.monitoredTransactions.nonEmpty)
      assert(outAESWriteDataMonitor.monitoredTransactions.size == 4)

      outAESWriteDataMonitor.monitoredTransactions
        .map(x => x.data)
        .zipWithIndex
        .foreach {case (o, i) => {
          assert (o(31,0).litValue() == inputs(i))
          assert (o(63,32).litValue() == AESAddr.TEXT.litValue() + 4 - 1 - i)
        }}
      outAESWriteDataMonitor.clearMonitoredTransactions()
      c.clock.step(50)

      assert (c.io.testCState.peek.litValue() == AESState.sWaitStart.litValue())
      var op_type = r.nextInt(2)
      var block_count = r.nextInt(4) + 1
      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.dcplrIO.block_count.poke(block_count.U(32.W))
      c.io.dcplrIO.op_type.poke(op_type.B)

      c.clock.step()
      assert (c.io.testCState.peek.litValue() == AESState.sAESRun.litValue())
      while (c.io.dcplrIO.start_ready.peek.litToBoolean) {
        c.clock.step()
      }
      c.io.dcplrIO.start_valid.poke(false.B)
      c.clock.step()

      c.clock.step(10)
      // check send aes enc/dec request
      assert (outAESCtrlMonitor.monitoredTransactions.nonEmpty)
      assert (outAESCtrlMonitor.monitoredTransactions.size == 1)
      assert (outAESCtrlMonitor.monitoredTransactions(0).data(31, 0).litValue() == 2)
      assert (outAESCtrlMonitor.monitoredTransactions(0).data(63, 32).litValue() == AESAddr.CTRL.litValue())
      outAESCtrlMonitor.clearMonitoredTransactions()

      c.io.aesCoreIO.read_data.poke(0.U)
      assert (c.io.testCState.peek.litValue() == AESState.sWaitResult.litValue())
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(100.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this

      assert (c.io.testCState.peek.litValue() == AESState.sDataWrite.litValue())
      c.clock.step(100) // Random, you can tweak this
      for (i <- 1 until block_count) {
        assert (c.io.testCState.peek.litValue() == AESState.sDataSetup.litValue())
        assert(!outReadReqMonitor.monitoredTransactions.isEmpty)
        assert(outReadReqMonitor.monitoredTransactions.size == 1)
        var req = outReadReqMonitor.monitoredTransactions.head
        outReadReqMonitor.clearMonitoredTransactions()
        assert(req.data.addr.litValue() == src_addr + 16 * i)
        assert(req.data.totalBytes.litValue() == 4 * 4)

        inputs = Seq.fill(4 * 4 / beatBytes)(BigInt((beatBytes * 8), scala.util.Random))
        inDriver.push(inputs.map(x => new DecoupledTX(UInt((beatBytes * 8).W)).tx(x.U)))
        c.clock.step(inputs.length + 200 )
        assert(outAESWriteDataMonitor.monitoredTransactions.nonEmpty)
        assert(outAESWriteDataMonitor.monitoredTransactions.size == 4)

        outAESWriteDataMonitor.monitoredTransactions
          .map(x => x.data)
          .zipWithIndex
          .foreach {case (o, i) => {
            assert (o(31,0).litValue() == inputs(i))
            assert (o(63,32).litValue() == AESAddr.TEXT.litValue() + 4 - 1 - i)
          }}
        outAESWriteDataMonitor.clearMonitoredTransactions()
        // check if controller send AES next request
        assert(outAESCtrlMonitor.monitoredTransactions.nonEmpty)
        assert(outAESCtrlMonitor.monitoredTransactions.size == 1)
        assert (outAESCtrlMonitor.monitoredTransactions(0).data(31, 0).litValue() == 2)
        assert (outAESCtrlMonitor.monitoredTransactions(0).data(63, 32).litValue() == AESAddr.CTRL.litValue())
        outAESCtrlMonitor.clearMonitoredTransactions()
        c.clock.step(r.nextInt(10) + 1)

        assert (c.io.testCState.peek.litValue() == AESState.sWaitResult.litValue())
        c.io.aesCoreIO.read_data.poke(0.U)
        c.clock.step(100) // Random, you can tweak this
        c.io.aesCoreIO.read_data.poke(1.U)
        assert (c.io.testCState.peek.litValue() == AESState.sWaitResult.litValue())
        c.clock.step()
        c.io.aesCoreIO.read_data.poke(100.U)
        assert (c.io.testCState.peek.litValue() == AESState.sDataWrite.litValue())
        c.clock.step(100) // Random, you can tweak this
      }
      assert (c.io.testCState.peek.litValue() == AESState.sIdle.litValue())
      // check that we get the correct write req
      assert(outWriteReqMonitor.monitoredTransactions.nonEmpty)
      assert(outWriteReqMonitor.monitoredTransactions.size == 4 * block_count)
      outWriteReqMonitor.monitoredTransactions
        .map(x => x.data)
        .zipWithIndex
        .foreach {case (o, i) => {
          //println(o.data.litValue(), o.addr.litValue(), i/4)
          assert (o.data.litValue() == 100)
          assert (o.addr.litValue() == (dest_addr + 16 * (i / 4) + (i %4) * beatBytes))
        }}
      assert(outAESReadDataMonitor.monitoredTransactions.nonEmpty)
      assert(outAESReadDataMonitor.monitoredTransactions.size == 5 * block_count)
    }
  }

  it should "test DMA interface & State Transitions (beatBytes = 2)" in {
    val beatBytes = 2
    test(new AESController(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      // Initializing RoCCCommand driver, receiver (dummy), and monitor
      // driver for dma readresp
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dmem.readRespQueue)
      val outAESWriteDataMonitor = new ValidMonitor(c.clock, c.io.testAESWriteData)
      val outAESReadDataMonitor = new ValidMonitor(c.clock, c.io.testAESReadData)
      val outAESCtrlMonitor = new ValidMonitor(c.clock, c.io.testAESCtrl)
      // driver for dma writereq
      val outWriteReqDriver = new DecoupledDriverSlave[EE290CDMAWriterReq](c.clock, c.io.dmem.writeReq, 0)
      val outWriteReqMonitor = new DecoupledMonitor[EE290CDMAWriterReq](c.clock, c.io.dmem.writeReq)
      val outReadReqDriver = new DecoupledDriverSlave[EE290CDMAReaderReq](c.clock, c.io.dmem.readReq, 0)
      val outReadReqMonitor = new DecoupledMonitor[EE290CDMAReaderReq](c.clock, c.io.dmem.readReq)
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
      c.clock.step(r.nextInt(10) + 1)
      c.io.reset.poke(false.B)
      c.clock.step()

      // Check state here
      assert (c.io.testCState.peek.litValue() == AESState.sIdle.litValue())

      // This is not necessary since the FSM should be in IDLE (from above assert), but it works as an example
      while (!c.io.dcplrIO.key_ready.peek.litToBoolean) {
        c.clock.step()
      }

      // Triggering key expansion
      var key_size = r.nextInt(2)
      var addr = r.nextInt(1 << 32)
      c.io.dcplrIO.key_valid.poke(true.B)
      c.io.dcplrIO.key_size.poke(key_size.U(1.W))
      c.io.dcplrIO.key_addr.poke(addr.U(32.W))

      c.clock.step()
      while (c.io.dcplrIO.key_ready.peek.litToBoolean) {
        c.clock.step()
      }
      c.io.dcplrIO.key_valid.poke(false.B)
      c.clock.step()
    
      // check sending aes key exp request
      // TODO: problem of peek after poke
      /*assert (!outAESCtrlMonitor.monitoredTransactions.nonEmpty)
      assert (outAESCtrlMonitor.monitoredTransactions.size == 1)
      assert (outAESCtrlMonitor.monitoredTransactions(0).data(31, 0).litValue() == 2)
      assert (outAESCtrlMonitor.monitoredTransactions(0).data(63, 32).litValue() == AESAddr.CONFIG.litValue())
      outAESCtrlMonitor.clearMonitoredTransactions()*/
      

      // Check state here
      assert (c.io.testCState.peek.litValue() == AESState.sKeySetup.litValue())
      c.clock.step(10)

      // 4 or 8 times depending on key size
      val times = if (key_size== 1) 8 else 4
      // Check that DMA  actually received a read request
      assert(!outReadReqMonitor.monitoredTransactions.isEmpty)
      var req = outReadReqMonitor.monitoredTransactions.head
      outReadReqMonitor.clearMonitoredTransactions()
      assert(req.data.addr.litValue() == addr)
      assert(req.data.totalBytes.litValue() == times * 4)

      assert(c.io.testMState.peek.litValue() == MemState.sReadIntoAES.litValue())
      // Send responses
      var inputs = Seq.fill(times * 4 / beatBytes)(BigInt((beatBytes * 8), scala.util.Random))
      var results = inputs.grouped(2).map { case List(x,y) => (y << 16) + x}.toList
      inDriver.push(inputs.map(x => new DecoupledTX(UInt((beatBytes * 8).W)).tx(x.U)))
      c.clock.step(inputs.length + 200 )

      // Now you check if the data is correctly presented to the AES Core
      assert(outAESWriteDataMonitor.monitoredTransactions.nonEmpty)
      assert(outAESWriteDataMonitor.monitoredTransactions.size == times)

      outAESWriteDataMonitor.monitoredTransactions
        .map(x => x.data)
        .zipWithIndex
        .foreach {case (o, i) => {
          assert (o(31,0).litValue() == results(i))
          assert (o(63,32).litValue() == AESAddr.KEY.litValue() + times - 1 - i)
        }}
      outAESWriteDataMonitor.clearMonitoredTransactions()
      c.clock.step(r.nextInt(10) + 1)

      assert (c.io.testCState.peek.litValue() == AESState.sKeyExp.litValue())
      c.io.aesCoreIO.read_data.poke(0.U)
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this

      assert (c.io.testCState.peek.litValue() == AESState.sWaitData.litValue())
      // Triggering text loading
      val src_addr = r.nextInt(1 << 32)
      val dest_addr = r.nextInt(1 << 32)
      c.io.dcplrIO.addr_valid.poke(true.B)
      c.io.dcplrIO.src_addr.poke(src_addr.U(32.W))
      c.io.dcplrIO.dest_addr.poke(dest_addr.U(32.W))

      c.clock.step()
      while (c.io.dcplrIO.addr_ready.peek.litToBoolean) {
        c.clock.step()
      }
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.clock.step()

      assert (c.io.testCState.peek.litValue() == AESState.sDataSetup.litValue())
      c.clock.step(10)

      assert(!outReadReqMonitor.monitoredTransactions.isEmpty)
      req = outReadReqMonitor.monitoredTransactions.head
      outReadReqMonitor.clearMonitoredTransactions()
      assert(req.data.addr.litValue() == src_addr)
      assert(req.data.totalBytes.litValue() == 4 * 4)

      assert(c.io.testMState.peek.litValue() == MemState.sReadIntoAES.litValue())
      // Send responses
      inputs = Seq.fill(4 * 4 / beatBytes)(BigInt((beatBytes * 8), scala.util.Random))
      results = inputs.grouped(2).map { case List(x,y) => (y << 16) + x}.toList
      inDriver.push(inputs.map(x => new DecoupledTX(UInt((beatBytes * 8).W)).tx(x.U)))
      c.clock.step(inputs.length + 200 )

      // Now you check if the data is correctly presented to the AES Core
      assert(outAESWriteDataMonitor.monitoredTransactions.nonEmpty)
      assert(outAESWriteDataMonitor.monitoredTransactions.size == 4)

      outAESWriteDataMonitor.monitoredTransactions
        .map(x => x.data)
        .zipWithIndex
        .foreach {case (o, i) => {
          assert (o(31,0).litValue() == results(i))
          assert (o(63,32).litValue() == AESAddr.TEXT.litValue() + 4 - 1 - i)
        }}
      outAESWriteDataMonitor.clearMonitoredTransactions()
      c.clock.step(50)

      assert (c.io.testCState.peek.litValue() == AESState.sWaitStart.litValue())
      var op_type = r.nextInt(2)
      var block_count = r.nextInt(4) + 1
      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.dcplrIO.block_count.poke(block_count.U(32.W))
      c.io.dcplrIO.op_type.poke(op_type.B)

      c.clock.step()
      assert (c.io.testCState.peek.litValue() == AESState.sAESRun.litValue())
      while (c.io.dcplrIO.start_ready.peek.litToBoolean) {
        c.clock.step()
      }
      c.io.dcplrIO.start_valid.poke(false.B)
      c.clock.step()

      c.clock.step(10)
      // check send aes enc/dec request
      assert (outAESCtrlMonitor.monitoredTransactions.nonEmpty)
      assert (outAESCtrlMonitor.monitoredTransactions.size == 1)
      assert (outAESCtrlMonitor.monitoredTransactions(0).data(31, 0).litValue() == 2)
      assert (outAESCtrlMonitor.monitoredTransactions(0).data(63, 32).litValue() == AESAddr.CTRL.litValue())
      outAESCtrlMonitor.clearMonitoredTransactions()

      c.io.aesCoreIO.read_data.poke(0.U)
      assert (c.io.testCState.peek.litValue() == AESState.sWaitResult.litValue())
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this

      assert (c.io.testCState.peek.litValue() == AESState.sDataWrite.litValue())
      c.clock.step(100) // Random, you can tweak this
      for (i <- 1 until block_count) {
        assert (c.io.testCState.peek.litValue() == AESState.sDataSetup.litValue())
        assert(!outReadReqMonitor.monitoredTransactions.isEmpty)
        assert(outReadReqMonitor.monitoredTransactions.size == 1)
        var req = outReadReqMonitor.monitoredTransactions.head
        outReadReqMonitor.clearMonitoredTransactions()
        assert(req.data.addr.litValue() == src_addr + 16 * i)
        assert(req.data.totalBytes.litValue() == 4 * 4)

        inputs = Seq.fill(4 * 4 / beatBytes)(BigInt((beatBytes * 8), scala.util.Random))
        results = inputs.grouped(2).map { case List(x,y) => (y << 16) + x}.toList
        inDriver.push(inputs.map(x => new DecoupledTX(UInt((beatBytes * 8).W)).tx(x.U)))
        c.clock.step(inputs.length + 200 )
        assert(outAESWriteDataMonitor.monitoredTransactions.nonEmpty)
        assert(outAESWriteDataMonitor.monitoredTransactions.size == 4)

        outAESWriteDataMonitor.monitoredTransactions
          .map(x => x.data)
          .zipWithIndex
          .foreach {case (o, i) => {
            assert (o(31,0).litValue() == results(i))
            assert (o(63,32).litValue() == AESAddr.TEXT.litValue() + 4 - 1 - i)
          }}
        outAESWriteDataMonitor.clearMonitoredTransactions()
        // check if controller send AES next request
        assert(outAESCtrlMonitor.monitoredTransactions.nonEmpty)
        assert(outAESCtrlMonitor.monitoredTransactions.size == 1)
        assert (outAESCtrlMonitor.monitoredTransactions(0).data(31, 0).litValue() == 2)
        assert (outAESCtrlMonitor.monitoredTransactions(0).data(63, 32).litValue() == AESAddr.CTRL.litValue())
        outAESCtrlMonitor.clearMonitoredTransactions()
        c.clock.step(r.nextInt(10) + 1)

        assert (c.io.testCState.peek.litValue() == AESState.sWaitResult.litValue())
        c.io.aesCoreIO.read_data.poke(0.U)
        c.clock.step(100) // Random, you can tweak this
        c.io.aesCoreIO.read_data.poke(1.U)
        c.clock.step()
        c.io.aesCoreIO.read_data.poke(100.U)
        assert (c.io.testCState.peek.litValue() == AESState.sDataWrite.litValue())
        c.clock.step(100) // Random, you can tweak this
      }
      assert (c.io.testCState.peek.litValue() == AESState.sIdle.litValue())
      // check that we received the correct nunmber of data write
      assert(outWriteReqMonitor.monitoredTransactions.nonEmpty)
      assert(outWriteReqMonitor.monitoredTransactions.size == 4 * 4 / beatBytes * block_count)
      assert(outAESReadDataMonitor.monitoredTransactions.nonEmpty)
      assert(outAESReadDataMonitor.monitoredTransactions.size == 5 * block_count)
    }
  }

  it should "test DMA interface & State Transitions (beatBytes = 8)" in {
    val beatBytes = 8
    test(new AESController(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      // Initializing RoCCCommand driver, receiver (dummy), and monitor
      // driver for dma readresp
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dmem.readRespQueue)
      val outAESWriteDataMonitor = new ValidMonitor(c.clock, c.io.testAESWriteData)
      val outAESReadDataMonitor = new ValidMonitor(c.clock, c.io.testAESReadData)
      val outAESCtrlMonitor = new ValidMonitor(c.clock, c.io.testAESCtrl)
      // driver for dma writereq
      val outWriteReqDriver = new DecoupledDriverSlave[EE290CDMAWriterReq](c.clock, c.io.dmem.writeReq, 0)
      val outWriteReqMonitor = new DecoupledMonitor[EE290CDMAWriterReq](c.clock, c.io.dmem.writeReq)
      val outReadReqDriver = new DecoupledDriverSlave[EE290CDMAReaderReq](c.clock, c.io.dmem.readReq, 0)
      val outReadReqMonitor = new DecoupledMonitor[EE290CDMAReaderReq](c.clock, c.io.dmem.readReq)
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
      c.clock.step(r.nextInt(10) + 1)
      c.io.reset.poke(false.B)
      c.clock.step()

      // Check state here
      assert (c.io.testCState.peek.litValue() == AESState.sIdle.litValue())

      // This is not necessary since the FSM should be in IDLE (from above assert), but it works as an example
      while (!c.io.dcplrIO.key_ready.peek.litToBoolean) {
        c.clock.step()
      }

      // Triggering key expansion
      var key_size = r.nextInt(2)
      var addr = r.nextInt(1 << 32)
      c.io.dcplrIO.key_valid.poke(true.B)
      c.io.dcplrIO.key_size.poke(key_size.U(1.W))
      c.io.dcplrIO.key_addr.poke(addr.U(32.W))

      c.clock.step()
      while (c.io.dcplrIO.key_ready.peek.litToBoolean) {
        c.clock.step()
      }
      c.io.dcplrIO.key_valid.poke(false.B)
      c.clock.step()
    
      // check sending aes key exp request
      // TODO: problem of peek after poke
      /*assert (!outAESCtrlMonitor.monitoredTransactions.nonEmpty)
      assert (outAESCtrlMonitor.monitoredTransactions.size == 1)
      assert (outAESCtrlMonitor.monitoredTransactions(0).data(31, 0).litValue() == 2)
      assert (outAESCtrlMonitor.monitoredTransactions(0).data(63, 32).litValue() == AESAddr.CONFIG.litValue())
      outAESCtrlMonitor.clearMonitoredTransactions()*/
      

      // Check state here
      assert (c.io.testCState.peek.litValue() == AESState.sKeySetup.litValue())
      c.clock.step(10)

      // 4 or 8 times depending on key size
      val times = if (key_size== 1) 8 else 4
      // Check that DMA  actually received a read request
      assert(!outReadReqMonitor.monitoredTransactions.isEmpty)
      var req = outReadReqMonitor.monitoredTransactions.head
      outReadReqMonitor.clearMonitoredTransactions()
      assert(req.data.addr.litValue() == addr)
      assert(req.data.totalBytes.litValue() == times * 4)

      assert(c.io.testMState.peek.litValue() == MemState.sReadIntoAES.litValue())
      // Send responses
      var inputs = Seq.fill(times * 4 / beatBytes)(BigInt((beatBytes * 8), scala.util.Random))
      var results = Seq[BigInt]()
      for (i <- inputs) {
        results = results ++ Seq(i & (BigInt("1" * 32, 2)), i >> 32)
      }
      inDriver.push(inputs.map(x => new DecoupledTX(UInt((beatBytes * 8).W)).tx(x.U)))
      c.clock.step(inputs.length * 2 + 200 )

      // Now you check if the data is correctly presented to the AES Core
      assert(outAESWriteDataMonitor.monitoredTransactions.nonEmpty)
      assert(outAESWriteDataMonitor.monitoredTransactions.size == times)

      outAESWriteDataMonitor.monitoredTransactions
        .map(x => x.data)
        .zipWithIndex
        .foreach {case (o, i) => {
          assert (o(31,0).litValue() == results(i))
          assert (o(63,32).litValue() == AESAddr.KEY.litValue() + times - 1 - i)
        }}
      outAESWriteDataMonitor.clearMonitoredTransactions()
      c.clock.step(r.nextInt(10) + 1)

      assert (c.io.testCState.peek.litValue() == AESState.sKeyExp.litValue())
      c.io.aesCoreIO.read_data.poke(0.U)
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this

      assert (c.io.testCState.peek.litValue() == AESState.sWaitData.litValue())
      // Triggering text loading
      val src_addr = r.nextInt(1 << 32)
      val dest_addr = r.nextInt(1 << 32)
      c.io.dcplrIO.addr_valid.poke(true.B)
      c.io.dcplrIO.src_addr.poke(src_addr.U(32.W))
      c.io.dcplrIO.dest_addr.poke(dest_addr.U(32.W))

      c.clock.step()
      while (c.io.dcplrIO.addr_ready.peek.litToBoolean) {
        c.clock.step()
      }
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.clock.step()

      assert (c.io.testCState.peek.litValue() == AESState.sDataSetup.litValue())
      c.clock.step(10)

      assert(!outReadReqMonitor.monitoredTransactions.isEmpty)
      req = outReadReqMonitor.monitoredTransactions.head
      outReadReqMonitor.clearMonitoredTransactions()
      assert(req.data.addr.litValue() == src_addr)
      assert(req.data.totalBytes.litValue() == 4 * 4)

      assert(c.io.testMState.peek.litValue() == MemState.sReadIntoAES.litValue())
      // Send responses
      inputs = Seq.fill(4 * 4 / beatBytes)(BigInt((beatBytes * 8), scala.util.Random))
      results = Seq[BigInt]()
      for (i <- inputs) {
        results = results ++ Seq(i & (BigInt("1" * 32, 2)), i >> 32)
      }
      inDriver.push(inputs.map(x => new DecoupledTX(UInt((beatBytes * 8).W)).tx(x.U)))
      c.clock.step(inputs.length + 200 )

      // Now you check if the data is correctly presented to the AES Core
      assert(outAESWriteDataMonitor.monitoredTransactions.nonEmpty)
      assert(outAESWriteDataMonitor.monitoredTransactions.size == 4)

      outAESWriteDataMonitor.monitoredTransactions
        .map(x => x.data)
        .zipWithIndex
        .foreach {case (o, i) => {
          assert (o(31,0).litValue() == results(i))
          assert (o(63,32).litValue() == AESAddr.TEXT.litValue() + 4 - 1 - i)
        }}
      outAESWriteDataMonitor.clearMonitoredTransactions()
      c.clock.step(50)

      assert (c.io.testCState.peek.litValue() == AESState.sWaitStart.litValue())
      var op_type = r.nextInt(2)
      var block_count = r.nextInt(4) + 1
      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.dcplrIO.block_count.poke(block_count.U(32.W))
      c.io.dcplrIO.op_type.poke(op_type.B)

      c.clock.step()
      assert (c.io.testCState.peek.litValue() == AESState.sAESRun.litValue())
      while (c.io.dcplrIO.start_ready.peek.litToBoolean) {
        c.clock.step()
      }
      c.io.dcplrIO.start_valid.poke(false.B)
      c.clock.step()

      c.clock.step(10)
      // check send aes enc/dec request
      assert (outAESCtrlMonitor.monitoredTransactions.nonEmpty)
      assert (outAESCtrlMonitor.monitoredTransactions.size == 1)
      assert (outAESCtrlMonitor.monitoredTransactions(0).data(31, 0).litValue() == 2)
      assert (outAESCtrlMonitor.monitoredTransactions(0).data(63, 32).litValue() == AESAddr.CTRL.litValue())
      outAESCtrlMonitor.clearMonitoredTransactions()

      c.io.aesCoreIO.read_data.poke(0.U)
      assert (c.io.testCState.peek.litValue() == AESState.sWaitResult.litValue())
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(100.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this

      assert (c.io.testCState.peek.litValue() == AESState.sDataWrite.litValue())
      c.clock.step(100) // Random, you can tweak this
      for (i <- 1 until block_count) {
        assert (c.io.testCState.peek.litValue() == AESState.sDataSetup.litValue())
        assert(!outReadReqMonitor.monitoredTransactions.isEmpty)
        assert(outReadReqMonitor.monitoredTransactions.size == 1)
        var req = outReadReqMonitor.monitoredTransactions.head
        outReadReqMonitor.clearMonitoredTransactions()
        assert(req.data.addr.litValue() == src_addr + 16 * i)
        assert(req.data.totalBytes.litValue() == 4 * 4)

        inputs = Seq.fill(4 * 4 / beatBytes)(BigInt((beatBytes * 8), scala.util.Random))
        results = Seq[BigInt]()
        for (i <- inputs) {
          results = results ++ Seq(i & (BigInt("1" * 32, 2)), i >> 32)
        }
        inDriver.push(inputs.map(x => new DecoupledTX(UInt((beatBytes * 8).W)).tx(x.U)))
        c.clock.step(inputs.length + 200 )
        assert(outAESWriteDataMonitor.monitoredTransactions.nonEmpty)
        assert(outAESWriteDataMonitor.monitoredTransactions.size == 4)

        outAESWriteDataMonitor.monitoredTransactions
          .map(x => x.data)
          .zipWithIndex
          .foreach {case (o, i) => {
            assert (o(31,0).litValue() == results(i))
            assert (o(63,32).litValue() == AESAddr.TEXT.litValue() + 4 - 1 - i)
          }}
        outAESWriteDataMonitor.clearMonitoredTransactions()
        // check if controller send AES next request
        assert(outAESCtrlMonitor.monitoredTransactions.nonEmpty)
        assert(outAESCtrlMonitor.monitoredTransactions.size == 1)
        assert (outAESCtrlMonitor.monitoredTransactions(0).data(31, 0).litValue() == 2)
        assert (outAESCtrlMonitor.monitoredTransactions(0).data(63, 32).litValue() == AESAddr.CTRL.litValue())
        outAESCtrlMonitor.clearMonitoredTransactions()
        c.clock.step(r.nextInt(10) + 1)

        assert (c.io.testCState.peek.litValue() == AESState.sWaitResult.litValue())
        c.io.aesCoreIO.read_data.poke(0.U)
        c.clock.step(100) // Random, you can tweak this
        c.io.aesCoreIO.read_data.poke(1.U)
        assert (c.io.testCState.peek.litValue() == AESState.sWaitResult.litValue())
        c.clock.step()
        c.io.aesCoreIO.read_data.poke(100.U)
        assert (c.io.testCState.peek.litValue() == AESState.sDataWrite.litValue())
        c.clock.step(100) // Random, you can tweak this
      }
      assert (c.io.testCState.peek.litValue() == AESState.sIdle.litValue())
      // check that we get the correct write req
      assert(outWriteReqMonitor.monitoredTransactions.nonEmpty)
      assert(outWriteReqMonitor.monitoredTransactions.size == 4 * block_count)
      outWriteReqMonitor.monitoredTransactions
        .map(x => x.data)
        .zipWithIndex
        .foreach {case (o, i) => {
          //println(o.data.litValue(), o.addr.litValue(), i/4)
          assert (o.data.litValue() == 100)
          assert (o.addr.litValue() == (dest_addr + 16 * (i / 4) + (i %4) * 4))
        }}
      assert(outAESReadDataMonitor.monitoredTransactions.nonEmpty)
      assert(outAESReadDataMonitor.monitoredTransactions.size == 5 * block_count)
    }
  }
}
