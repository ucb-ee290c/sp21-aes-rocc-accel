package aes

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


class CtrlSanityTest extends AnyFlatSpec with ChiselScalatestTester {
  implicit val p: Parameters = VerifTestUtils.getVerifParameters(xLen = 32) // 32-bit processor

  it should "elaborate the controller" in {
    //.withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation))
    test(new AESController(32, 8)) { c =>
      assert(true)
    }
  }

  it should "sanity check controller reset" in {
    val beatBytes = 4
    test(new AESController(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      c.io.dcplrIO.key_valid.poke(false.B)
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.io.dcplrIO.start_valid.poke(false.B)
      c.clock.step()
      
      // test when state is in IDLE 
      c.io.dcplrIO.key_valid.poke(true.B)
      c.io.reset.poke(true.B)
      c.clock.step()
      c.io.dcplrIO.key_valid.poke(false.B)
      c.io.reset.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
      c.clock.step()

      // test when state is in WaitData
      c.io.setCValid.poke(true.B)
      c.io.setCState.poke(CtrlState.sWaitData.litValue().U)
      c.clock.step()
      c.io.setCValid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitData.litValue())

      c.io.dcplrIO.addr_valid.poke(true.B)
      c.io.reset.poke(true.B)
      c.clock.step()
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.io.reset.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
      c.clock.step()

      // test when state is in WaitStart
      c.io.setCValid.poke(true.B)
      c.io.setCState.poke(CtrlState.sWaitStart.litValue().U)
      c.clock.step()
      c.io.setCValid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitStart.litValue())

      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.reset.poke(true.B)
      c.clock.step()
      c.io.dcplrIO.start_valid.poke(false.B)
      c.io.reset.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
      c.clock.step()

      // test when state is in WaitResult
      c.io.setCValid.poke(true.B)
      c.io.setCState.poke(CtrlState.sWaitResult.litValue().U)
      c.clock.step()
      c.io.setCValid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())

      c.io.aesCoreIO.read_data.poke(0.U)
      c.clock.step()
      c.io.aesCoreIO.read_data.poke(1.U)
      c.io.reset.poke(true.B)
      c.clock.step()
      c.io.aesCoreIO.read_data.poke(0.U)
      c.io.reset.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
      c.clock.step()

      // test when state is in DataWrite & write not done
      c.io.setCValid.poke(true.B)
      c.io.setCState.poke(CtrlState.sDataWrite.litValue().U)
      c.io.setMValid.poke(true.B)
      c.io.setMState.poke(MemState.sWriteIntoMem.litValue().U)
      c.clock.step()
      c.io.setCValid.poke(false.B)
      c.io.setMValid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())

      c.io.reset.poke(true.B)
      c.clock.step()
      c.io.reset.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
      c.clock.step()
      
      // test when memory state is in ReadReq
      c.io.setMValid.poke(true.B)
      c.io.setMState.poke(MemState.sReadReq.litValue().U)
      c.clock.step()
      c.io.setMValid.poke(false.B)
      assert (c.io.testMState.peek.litValue() == MemState.sReadReq.litValue())

      c.io.dmem.readReq.ready.poke(true.B)
      c.io.reset.poke(true.B)
      c.clock.step()
      c.io.dmem.readReq.ready.poke(false.B)
      c.io.reset.poke(false.B)
      assert (c.io.testMState.peek.litValue() == MemState.sIdle.litValue())
      c.clock.step()

      // test when memory state is in WriteReq
      c.io.setMValid.poke(true.B)
      c.io.setMState.poke(MemState.sWriteReq.litValue().U)
      c.clock.step()
      c.io.setMValid.poke(false.B)
      assert (c.io.testMState.peek.litValue() == MemState.sWriteReq.litValue())

      c.io.dmem.writeReq.ready.poke(true.B)
      c.clock.step(2)
      c.io.reset.poke(true.B)
      c.clock.step(1)
      c.io.dmem.writeReq.ready.poke(false.B)
      c.io.reset.poke(false.B)
      assert (c.io.testMState.peek.litValue() == MemState.sIdle.litValue())
      c.clock.step()
    }
  }

  it should "sanity check controller interrupt" in {
    val beatBytes = 4
    test(new AESController(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      // transition to DataWrite state
      val r = new Random
      c.io.aesCoreIO.read_data.poke(1.U)
      c.io.setCValid.poke(true.B)
      c.io.setCState.poke(CtrlState.sWaitStart.litValue().U)
      c.clock.step()
      c.io.setCValid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitStart.litValue())
      
      var block_count = r.nextInt(10) + 1
      var intrpt_en = 1
      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.dcplrIO.op_type.poke(true.B)
      c.io.dcplrIO.block_count.poke(block_count.U)
      c.io.dcplrIO.intrpt_en.poke(intrpt_en.B)
      c.clock.step()
      c.io.dcplrIO.start_valid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sAESRun.litValue())
      c.clock.step()

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
      c.io.aesCoreIO.read_data.poke(0.U)
      c.clock.step(r.nextInt(10) + 1)
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step()

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
      c.io.dmem.writeReq.ready.poke(true.B)
      while (c.io.testMState.peek.litValue() != MemState.sIdle.litValue()) {
        c.clock.step()
      }
      c.io.dmem.readRespQueue.valid.poke(true.B)
      c.io.dmem.readReq.ready.poke(true.B)
      for (i <- 1 until block_count) {
        assert (!c.io.dcplrIO.interrupt.peek.litToBoolean)
        c.clock.step()
        assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
        c.clock.step(r.nextInt(5) + 20)

        assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
        c.io.aesCoreIO.read_data.poke(0.U)
        c.clock.step(r.nextInt(10) + 1)
        c.io.aesCoreIO.read_data.poke(1.U)
        c.clock.step()

        assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
        c.io.dmem.writeReq.ready.poke(true.B)
        while (c.io.testMState.peek.litValue() != MemState.sIdle.litValue()) {
          c.clock.step()
        }
      }
      assert (c.io.dcplrIO.interrupt.peek.litToBoolean)
      c.clock.step()
      assert (!c.io.dcplrIO.interrupt.peek.litToBoolean)

      c.io.aesCoreIO.read_data.poke(1.U)
      c.io.setCValid.poke(true.B)
      c.io.setCState.poke(CtrlState.sWaitStart.litValue().U)
      c.clock.step()
      c.io.setCValid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitStart.litValue())

      block_count = r.nextInt(10) + 1
      intrpt_en = 0
      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.dcplrIO.op_type.poke(true.B)
      c.io.dcplrIO.block_count.poke(block_count.U)
      c.io.dcplrIO.intrpt_en.poke(intrpt_en.B)
      c.clock.step()
      c.io.dcplrIO.start_valid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sAESRun.litValue())
      c.clock.step()

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
      c.io.aesCoreIO.read_data.poke(0.U)
      c.clock.step(r.nextInt(10) + 1)
    }
  }

  it should "sanity check controller exception" in {
    val beatBytes = 4
    test(new AESController(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      c.io.dcplrIO.key_valid.poke(false.B)
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.io.dcplrIO.start_valid.poke(false.B)
      c.clock.step()
      
      // test when state is in IDLE 
      c.io.dcplrIO.key_valid.poke(true.B)
      c.io.dcplrIO.excp_valid.poke(true.B)
      c.clock.step()
      c.io.dcplrIO.key_valid.poke(false.B)
      c.io.dcplrIO.excp_valid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
      c.clock.step()

      // test when state is in WaitData
      c.io.setCValid.poke(true.B)
      c.io.setCState.poke(CtrlState.sWaitData.litValue().U)
      c.clock.step()
      c.io.setCValid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitData.litValue())

      c.io.dcplrIO.addr_valid.poke(true.B)
      c.io.dcplrIO.excp_valid.poke(true.B)
      c.clock.step()
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.io.dcplrIO.excp_valid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
      c.clock.step()

      // test when state is in WaitStart
      c.io.setCValid.poke(true.B)
      c.io.setCState.poke(CtrlState.sWaitStart.litValue().U)
      c.clock.step()
      c.io.setCValid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitStart.litValue())

      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.dcplrIO.excp_valid.poke(true.B)
      c.clock.step()
      c.io.dcplrIO.start_valid.poke(false.B)
      c.io.dcplrIO.excp_valid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
      c.clock.step()

      // test when state is in WaitResult
      c.io.setCValid.poke(true.B)
      c.io.setCState.poke(CtrlState.sWaitResult.litValue().U)
      c.clock.step()
      c.io.setCValid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())

      c.io.aesCoreIO.read_data.poke(0.U)
      c.clock.step()
      c.io.aesCoreIO.read_data.poke(1.U)
      c.io.dcplrIO.excp_valid.poke(true.B)
      c.clock.step()
      c.io.aesCoreIO.read_data.poke(0.U)
      c.io.dcplrIO.excp_valid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
      c.clock.step()

      // test when state is in DataWrite & write not done
      c.io.setCValid.poke(true.B)
      c.io.setCState.poke(CtrlState.sDataWrite.litValue().U)
      c.io.setMValid.poke(true.B)
      c.io.setMState.poke(MemState.sWriteIntoMem.litValue().U)
      c.clock.step()
      c.io.setCValid.poke(false.B)
      c.io.setMValid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())

      c.io.dcplrIO.excp_valid.poke(true.B)
      c.clock.step()
      c.io.dcplrIO.excp_valid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
      c.clock.step()
      
      // test when memory state is in ReadReq
      c.io.setMValid.poke(true.B)
      c.io.setMState.poke(MemState.sReadReq.litValue().U)
      c.clock.step()
      c.io.setMValid.poke(false.B)
      assert (c.io.testMState.peek.litValue() == MemState.sReadReq.litValue())

      c.io.dmem.readReq.ready.poke(true.B)
      c.io.dcplrIO.excp_valid.poke(true.B)
      c.clock.step()
      c.io.dmem.readReq.ready.poke(false.B)
      c.io.dcplrIO.excp_valid.poke(false.B)
      assert (c.io.testMState.peek.litValue() == MemState.sIdle.litValue())
      c.clock.step()

      // test when memory state is in WriteReq
      c.io.setMValid.poke(true.B)
      c.io.setMState.poke(MemState.sWriteReq.litValue().U)
      c.clock.step()
      c.io.setMValid.poke(false.B)
      assert (c.io.testMState.peek.litValue() == MemState.sWriteReq.litValue())

      c.io.dmem.writeReq.ready.poke(true.B)
      c.clock.step(2)
      c.io.dcplrIO.excp_valid.poke(true.B)
      c.clock.step(1)
      c.io.dmem.writeReq.ready.poke(false.B)
      c.io.dcplrIO.excp_valid.poke(false.B)
      assert (c.io.testMState.peek.litValue() == MemState.sIdle.litValue())
      c.clock.step()
    }
  }

  it should "sanity check wrong valid signals" in {
    val beatBytes = 4
    test(new AESController(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      val r = new Random
      c.io.dcplrIO.key_valid.poke(false.B)
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.io.dcplrIO.start_valid.poke(false.B)
      c.clock.step()
      
      // test when state is in IDLE 
      c.io.dcplrIO.key_valid.poke(true.B)
      c.io.dcplrIO.addr_valid.poke(true.B)
      c.io.dcplrIO.start_valid.poke(true.B)
      c.clock.step(r.nextInt(10) + 1)
      c.io.dcplrIO.key_valid.poke(false.B)
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.io.dcplrIO.start_valid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sKeySetup.litValue())
      c.clock.step()

      // test when state is in WaitData
      c.io.setCValid.poke(true.B)
      c.io.setCState.poke(CtrlState.sWaitData.litValue().U)
      c.clock.step()
      c.io.setCValid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitData.litValue())

      c.io.dcplrIO.key_valid.poke(true.B)
      c.io.dcplrIO.addr_valid.poke(true.B)
      c.io.dcplrIO.start_valid.poke(true.B)
      c.clock.step(r.nextInt(10) + 1)
      c.io.dcplrIO.key_valid.poke(false.B)
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.io.dcplrIO.start_valid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
      c.clock.step()

      // test when state is in WaitStart
      c.io.setCValid.poke(true.B)
      c.io.setCState.poke(CtrlState.sWaitStart.litValue().U)
      c.clock.step()
      c.io.setCValid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitStart.litValue())

      c.io.dcplrIO.key_valid.poke(true.B)
      c.io.dcplrIO.addr_valid.poke(true.B)
      c.io.dcplrIO.start_valid.poke(true.B)
      c.clock.step()
      c.io.dcplrIO.key_valid.poke(false.B)
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.io.dcplrIO.start_valid.poke(false.B)
      assert (c.io.testCState.peek.litValue() == CtrlState.sAESRun.litValue())
      c.clock.step()
    }
  }

  it should "sanity check Key Setup Process (beatBytes = 4)" in {
    val beatBytes = 4
    test(new AESController(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      // AES signal driver/monitor
      val outAESWriteDataMonitor = new ValidMonitor(c.clock, c.io.testAESWriteData)
      val outAESReadDataMonitor = new ValidMonitor(c.clock, c.io.testAESReadData)
      val outAESCtrlMonitor = new ValidMonitor(c.clock, c.io.testAESCtrl)
      // DMA signal driver/monitor
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dmem.readRespQueue)
      val outReadReqDriver = new DecoupledDriverSlave[EE290CDMAReaderReq](c.clock, c.io.dmem.readReq, 0)
      val outReadReqMonitor = new DecoupledMonitor[EE290CDMAReaderReq](c.clock, c.io.dmem.readReq)
      val r = new Random
      c.io.dcplrIO.key_valid.poke(false.B)
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.io.dcplrIO.start_valid.poke(false.B)
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step()
      
      // Triggering key setup
      var key_size = r.nextInt(2)
      var addr = r.nextInt(1 << 32)
      c.io.dcplrIO.key_valid.poke(true.B)
      c.io.dcplrIO.key_size.poke(key_size.U(1.W))
      c.io.dcplrIO.key_addr.poke(addr.U(32.W))

      while (c.io.dcplrIO.key_ready.peek.litToBoolean) {
        assert (c.io.aesCoreIO.cs.peek.litToBoolean)
        assert (c.io.aesCoreIO.we.peek.litToBoolean)
        assert (c.io.aesCoreIO.write_data.peek.litValue() == key_size << 1)
        assert (c.io.aesCoreIO.address.peek.litValue() == AESAddr.CONFIG.litValue())
        c.clock.step()
      }
      c.io.dcplrIO.key_valid.poke(false.B)
      c.clock.step()
    
      // Check state here
      assert (c.io.testCState.peek.litValue() == CtrlState.sKeySetup.litValue())
      assert (c.io.testMState.peek.litValue() != MemState.sIdle.litValue())
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

      // check if the data is correctly presented to the AES Core
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sKeyExp.litValue())
      c.io.aesCoreIO.read_data.poke(0.U)
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this
      assert (outAESCtrlMonitor.monitoredTransactions.nonEmpty)
      outAESCtrlMonitor.monitoredTransactions
        .map(x => x.data)
        .zipWithIndex
        .foreach {case (o, i) => {
          assert (o(31,0).litValue() == 0)
          assert (o(63,32).litValue() == AESAddr.STATUS.litValue())
        }}
      outAESCtrlMonitor.clearMonitoredTransactions()

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitData.litValue())
    }
  }

  it should "sanity check Data Setup Process (beatBytes = 4)" in {
    val beatBytes = 4
    test(new AESController(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      // AES signal driver/monitor
      val outAESWriteDataMonitor = new ValidMonitor(c.clock, c.io.testAESWriteData)
      val outAESReadDataMonitor = new ValidMonitor(c.clock, c.io.testAESReadData)
      val outAESCtrlMonitor = new ValidMonitor(c.clock, c.io.testAESCtrl)
      // DMA signal driver/monitor
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dmem.readRespQueue)
      val outReadReqDriver = new DecoupledDriverSlave[EE290CDMAReaderReq](c.clock, c.io.dmem.readReq, 0)
      val outReadReqMonitor = new DecoupledMonitor[EE290CDMAReaderReq](c.clock, c.io.dmem.readReq)
      val r = new Random
      c.io.setCValid.poke(true.B)
      c.io.setCState.poke(CtrlState.sWaitData.litValue().U)
      c.clock.step()
      c.io.setCValid.poke(false.B)

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitData.litValue())
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
      c.clock.step(10)

      assert(outReadReqMonitor.monitoredTransactions.nonEmpty)
      val req = outReadReqMonitor.monitoredTransactions.head
      outReadReqMonitor.clearMonitoredTransactions()
      assert(req.data.addr.litValue() == src_addr)
      assert(req.data.totalBytes.litValue() == 4 * 4)

      assert(c.io.testMState.peek.litValue() == MemState.sReadIntoAES.litValue())
      // Send responses
      val inputs = Seq.fill(4 * 4 / beatBytes)(BigInt((beatBytes * 8), scala.util.Random))
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitStart.litValue())
    }
  }

  it should "sanity check Data Write Process (beatBytes = 4)" in {
    val beatBytes = 4
    test(new AESController(32, beatBytes)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      // AES signal driver/monitor
      val outAESWriteDataMonitor = new ValidMonitor(c.clock, c.io.testAESWriteData)
      val outAESReadDataMonitor = new ValidMonitor(c.clock, c.io.testAESReadData)
      val outAESCtrlMonitor = new ValidMonitor(c.clock, c.io.testAESCtrl)
      // DMA signal driver/monitor
      val inDriver = new DecoupledDriverMaster(c.clock, c.io.dmem.readRespQueue)
      val outReadReqDriver = new DecoupledDriverSlave[EE290CDMAReaderReq](c.clock, c.io.dmem.readReq, 0)
      val outReadReqMonitor = new DecoupledMonitor[EE290CDMAReaderReq](c.clock, c.io.dmem.readReq)
      val r = new Random
      c.io.setCValid.poke(true.B)
      c.io.setCState.poke(CtrlState.sWaitData.litValue().U)
      c.clock.step()
      c.io.setCValid.poke(false.B)

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitData.litValue())
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
      c.clock.step(10)

      assert(outReadReqMonitor.monitoredTransactions.nonEmpty)
      val req = outReadReqMonitor.monitoredTransactions.head
      outReadReqMonitor.clearMonitoredTransactions()
      assert(req.data.addr.litValue() == src_addr)
      assert(req.data.totalBytes.litValue() == 4 * 4)

      assert(c.io.testMState.peek.litValue() == MemState.sReadIntoAES.litValue())
      // Send responses
      val inputs = Seq.fill(4 * 4 / beatBytes)(BigInt((beatBytes * 8), scala.util.Random))
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitStart.litValue())
      var op_type = r.nextInt(2)
      var block_count = r.nextInt(5) + 1
      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.dcplrIO.op_type.poke(op_type.B)
      c.io.dcplrIO.block_count.poke(block_count.U)

      while (c.io.dcplrIO.start_ready.peek.litToBoolean) {
        assert (c.io.aesCoreIO.cs.peek.litToBoolean)
        assert (c.io.aesCoreIO.we.peek.litToBoolean)
        assert (c.io.aesCoreIO.write_data.peek.litValue() == op_type)
        assert (c.io.aesCoreIO.address.peek.litValue() == AESAddr.CONFIG.litValue())
        c.clock.step()
      }
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.clock.step(10)

      // check send aes enc/dec request
      assert (outAESCtrlMonitor.monitoredTransactions.nonEmpty)
      assert (outAESCtrlMonitor.monitoredTransactions.size == 1)
      assert (outAESCtrlMonitor.monitoredTransactions(0).data(31, 0).litValue() == 2)
      assert (outAESCtrlMonitor.monitoredTransactions(0).data(63, 32).litValue() == AESAddr.CTRL.litValue())
      outAESCtrlMonitor.clearMonitoredTransactions()

      c.io.aesCoreIO.read_data.poke(0.U)
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step() // Random, you can tweak this
      assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
      var counter = 0
      val results = Seq.fill(4)(BigInt((4 * 8), scala.util.Random))
      while (counter != 4) {
        if (c.io.aesCoreIO.cs.peek.litToBoolean && c.io.aesCoreIO.address.peek.litValue() == AESAddr.RESULT.litValue() + 3 - counter) {
          c.clock.step()
          c.io.aesCoreIO.read_data.poke(results(counter).U)
          counter = counter + 1
        } else {
          c.clock.step()
        }
      }
      c.clock.step(10)
      if (block_count == 1) assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
      else assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
      assert(outAESReadDataMonitor.monitoredTransactions.nonEmpty)
      assert(outAESReadDataMonitor.monitoredTransactions.size == 4)

      outAESReadDataMonitor.monitoredTransactions
        .map(x => x.data)
        .zipWithIndex
        .foreach {case (o, i) => {
          assert (o(31,0).litValue() == results(i))
        }}
      outAESReadDataMonitor.clearMonitoredTransactions()
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this
    }
  }

  it should "sanity check DMA interface & State Transitions (beatBytes = 4)" in {
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
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())

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
      assert (c.io.testCState.peek.litValue() == CtrlState.sKeySetup.litValue())
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sKeyExp.litValue())
      c.io.aesCoreIO.read_data.poke(0.U)
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this
      outAESCtrlMonitor.clearMonitoredTransactions()

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitData.litValue())
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitStart.litValue())
      var op_type = r.nextInt(2)
      var block_count = r.nextInt(4) + 1
      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.dcplrIO.block_count.poke(block_count.U(32.W))
      c.io.dcplrIO.op_type.poke(op_type.B)

      c.clock.step()
      assert (c.io.testCState.peek.litValue() == CtrlState.sAESRun.litValue())
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
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(100.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this
      outAESCtrlMonitor.clearMonitoredTransactions()

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
      c.clock.step(100) // Random, you can tweak this
      for (i <- 1 until block_count) {
        assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
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

        assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
        c.io.aesCoreIO.read_data.poke(0.U)
        c.clock.step(100) // Random, you can tweak this
        c.io.aesCoreIO.read_data.poke(1.U)
        assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
        c.clock.step()
        c.io.aesCoreIO.read_data.poke(100.U)
        assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
        c.clock.step(100) // Random, you can tweak this
      }
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
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
      assert(outAESReadDataMonitor.monitoredTransactions.size == 4 * block_count)
    }
  }

  it should "sanity check DMA interface & State Transitions (beatBytes = 2)" in {
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
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())

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
      assert (c.io.testCState.peek.litValue() == CtrlState.sKeySetup.litValue())
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sKeyExp.litValue())
      c.io.aesCoreIO.read_data.poke(0.U)
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this
      outAESCtrlMonitor.clearMonitoredTransactions()

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitData.litValue())
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitStart.litValue())
      var op_type = r.nextInt(2)
      var block_count = r.nextInt(4) + 1
      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.dcplrIO.block_count.poke(block_count.U(32.W))
      c.io.dcplrIO.op_type.poke(op_type.B)

      c.clock.step()
      assert (c.io.testCState.peek.litValue() == CtrlState.sAESRun.litValue())
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
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this
      outAESCtrlMonitor.clearMonitoredTransactions()

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
      c.clock.step(100) // Random, you can tweak this
      for (i <- 1 until block_count) {
        assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
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

        assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
        c.io.aesCoreIO.read_data.poke(0.U)
        c.clock.step(100) // Random, you can tweak this
        c.io.aesCoreIO.read_data.poke(1.U)
        c.clock.step()
        c.io.aesCoreIO.read_data.poke(100.U)
        assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
        c.clock.step(100) // Random, you can tweak this
      }
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
      // check that we received the correct nunmber of data write
      assert(outWriteReqMonitor.monitoredTransactions.nonEmpty)
      assert(outWriteReqMonitor.monitoredTransactions.size == 4 * 4 / beatBytes * block_count)
      assert(outAESReadDataMonitor.monitoredTransactions.nonEmpty)
      assert(outAESReadDataMonitor.monitoredTransactions.size == 4 * block_count)
    }
  }

  it should "sanity check DMA interface & State Transitions (beatBytes = 8)" in {
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
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())

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
      assert (c.io.testCState.peek.litValue() == CtrlState.sKeySetup.litValue())
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sKeyExp.litValue())
      c.io.aesCoreIO.read_data.poke(0.U)
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this
      outAESCtrlMonitor.clearMonitoredTransactions()

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitData.litValue())
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitStart.litValue())
      var op_type = r.nextInt(2)
      var block_count = r.nextInt(4) + 1
      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.dcplrIO.block_count.poke(block_count.U(32.W))
      c.io.dcplrIO.op_type.poke(op_type.B)

      c.clock.step()
      assert (c.io.testCState.peek.litValue() == CtrlState.sAESRun.litValue())
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
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(1) // Random, you can tweak this
      outAESCtrlMonitor.clearMonitoredTransactions()
      c.io.aesCoreIO.read_data.poke(100.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
      c.clock.step(100) // Random, you can tweak this
      for (i <- 1 until block_count) {
        assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
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

        assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
        c.io.aesCoreIO.read_data.poke(0.U)
        c.clock.step(100) // Random, you can tweak this
        c.io.aesCoreIO.read_data.poke(1.U)
        assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
        c.clock.step()
        c.io.aesCoreIO.read_data.poke(100.U)
        assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
        c.clock.step(100) // Random, you can tweak this
      }
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
      // check that we get the correct write req
      assert(outWriteReqMonitor.monitoredTransactions.nonEmpty)
      assert(outWriteReqMonitor.monitoredTransactions.size == 2 * block_count)
      outWriteReqMonitor.monitoredTransactions
        .map(x => x.data)
        .zipWithIndex
        .foreach {case (o, i) => {
          // println(o.data.litValue(), o.addr.litValue(), i/2)
          // assert (o.data.litValue() == 100)
          assert (o.addr.litValue() == (dest_addr + 16 * (i / 2) + (i%2) * 8))
        }}
      assert(outAESReadDataMonitor.monitoredTransactions.nonEmpty)
      assert(outAESReadDataMonitor.monitoredTransactions.size == 4 * block_count)
    }
  }

  it should "sanity check DMA interface & IDLE to DataSetup Transitions (beatBytes = 4)" in {
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
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())

      // This is not necessary since the FSM should be in IDLE (from above assert), but it works as an example
      while (!c.io.dcplrIO.start_ready.peek.litToBoolean) {
        c.clock.step()
      }

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

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
      c.clock.step(10)

      assert(!outReadReqMonitor.monitoredTransactions.isEmpty)
      var req = outReadReqMonitor.monitoredTransactions.head
      outReadReqMonitor.clearMonitoredTransactions()
      assert(req.data.addr.litValue() == src_addr)
      assert(req.data.totalBytes.litValue() == 4 * 4)

      assert(c.io.testMState.peek.litValue() == MemState.sReadIntoAES.litValue())
      // Send responses
      var inputs = Seq.fill(4 * 4 / beatBytes)(BigInt((beatBytes * 8), scala.util.Random))
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitStart.litValue())
      var op_type = r.nextInt(2)
      var block_count = r.nextInt(4) + 1
      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.dcplrIO.block_count.poke(block_count.U(32.W))
      c.io.dcplrIO.op_type.poke(op_type.B)

      c.clock.step()
      assert (c.io.testCState.peek.litValue() == CtrlState.sAESRun.litValue())
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
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(100.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this
      outAESCtrlMonitor.clearMonitoredTransactions()

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
      c.clock.step(100) // Random, you can tweak this
      for (i <- 1 until block_count) {
        assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
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

        assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
        c.io.aesCoreIO.read_data.poke(0.U)
        c.clock.step(100) // Random, you can tweak this
        c.io.aesCoreIO.read_data.poke(1.U)
        assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
        c.clock.step()
        c.io.aesCoreIO.read_data.poke(100.U)
        assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
        c.clock.step(100) // Random, you can tweak this
      }
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
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
      assert(outAESReadDataMonitor.monitoredTransactions.size == 4 * block_count)
    }
  }
  it should "sanity check DMA interface & IDEL to DataSetup after one round (beatBytes = 4)" in {
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
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())

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
      assert (c.io.testCState.peek.litValue() == CtrlState.sKeySetup.litValue())
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sKeyExp.litValue())
      c.io.aesCoreIO.read_data.poke(0.U)
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this
      outAESCtrlMonitor.clearMonitoredTransactions()

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitData.litValue())
      // Triggering text loading
      var src_addr = r.nextInt(1 << 32)
      var dest_addr = r.nextInt(1 << 32)
      c.io.dcplrIO.addr_valid.poke(true.B)
      c.io.dcplrIO.src_addr.poke(src_addr.U(32.W))
      c.io.dcplrIO.dest_addr.poke(dest_addr.U(32.W))

      c.clock.step()
      while (c.io.dcplrIO.addr_ready.peek.litToBoolean) {
        c.clock.step()
      }
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.clock.step()

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitStart.litValue())
      var op_type = r.nextInt(2)
      var block_count = r.nextInt(4) + 1
      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.dcplrIO.block_count.poke(block_count.U(32.W))
      c.io.dcplrIO.op_type.poke(op_type.B)

      c.clock.step()
      assert (c.io.testCState.peek.litValue() == CtrlState.sAESRun.litValue())
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
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(100.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this
      outAESCtrlMonitor.clearMonitoredTransactions()

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
      c.clock.step(100) // Random, you can tweak this
      for (i <- 1 until block_count) {
        assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
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

        assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
        c.io.aesCoreIO.read_data.poke(0.U)
        c.clock.step(100) // Random, you can tweak this
        c.io.aesCoreIO.read_data.poke(1.U)
        assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
        c.clock.step()
        c.io.aesCoreIO.read_data.poke(100.U)
        assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
        c.clock.step(100) // Random, you can tweak this
      }
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
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
      assert(outAESReadDataMonitor.monitoredTransactions.size == 4 * block_count)

      outWriteReqMonitor.clearMonitoredTransactions()
      outAESReadDataMonitor.clearMonitoredTransactions()

      while (!c.io.dcplrIO.start_ready.peek.litToBoolean) {
        c.clock.step()
      }

      // Triggering text loading
      src_addr = r.nextInt(1 << 32)
      dest_addr = r.nextInt(1 << 32)
      c.io.dcplrIO.addr_valid.poke(true.B)
      c.io.dcplrIO.src_addr.poke(src_addr.U(32.W))
      c.io.dcplrIO.dest_addr.poke(dest_addr.U(32.W))

      c.clock.step()
      while (c.io.dcplrIO.addr_ready.peek.litToBoolean) {
        c.clock.step()
      }
      c.io.dcplrIO.addr_valid.poke(false.B)
      c.clock.step()

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
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

      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitStart.litValue())
      op_type = r.nextInt(2)
      block_count = r.nextInt(4) + 1
      c.io.dcplrIO.start_valid.poke(true.B)
      c.io.dcplrIO.block_count.poke(block_count.U(32.W))
      c.io.dcplrIO.op_type.poke(op_type.B)

      c.clock.step()
      assert (c.io.testCState.peek.litValue() == CtrlState.sAESRun.litValue())
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
      assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
      c.clock.step(r.nextInt(100) + 1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(1.U)
      c.clock.step(1) // Random, you can tweak this
      c.io.aesCoreIO.read_data.poke(100.U)
      c.clock.step(r.nextInt(10) + 1) // Random, you can tweak this
      outAESCtrlMonitor.clearMonitoredTransactions()

      assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
      c.clock.step(100) // Random, you can tweak this
      for (i <- 1 until block_count) {
        assert (c.io.testCState.peek.litValue() == CtrlState.sDataSetup.litValue())
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

        assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
        c.io.aesCoreIO.read_data.poke(0.U)
        c.clock.step(100) // Random, you can tweak this
        c.io.aesCoreIO.read_data.poke(1.U)
        assert (c.io.testCState.peek.litValue() == CtrlState.sWaitResult.litValue())
        c.clock.step()
        c.io.aesCoreIO.read_data.poke(100.U)
        assert (c.io.testCState.peek.litValue() == CtrlState.sDataWrite.litValue())
        c.clock.step(100) // Random, you can tweak this
      }
      assert (c.io.testCState.peek.litValue() == CtrlState.sIdle.litValue())
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
      assert(outAESReadDataMonitor.monitoredTransactions.size == 4 * block_count)
    }
  }
}
