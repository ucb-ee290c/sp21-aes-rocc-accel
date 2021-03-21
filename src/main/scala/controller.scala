package aes

import chisel3._
import chisel3.util._
import chisel3.util.{Valid}
import chipsalliance.rocketchip.config.Parameters

class AESControllerIO(addrBits: Int, beatBytes: Int)(implicit p: Parameters) extends Bundle {
  // System
  val reset       = Input(Bool())

  // RoCC Decoupler
  val dcplrIO     = new DecouplerControllerIO
  val dmem        = new ControllerDMAIO(addrBits, beatBytes)

  // AES Core
  val aesCoreIO   = Flipped(new AESCoreIO)

  // Outputs for testing (TODO: Remove when done)
  val testAESWriteData = Valid(UInt(64.W))
  val testAESReadData = Valid(UInt(64.W))
  val testAESCtrl = Valid(UInt(64.W))
  val testRemain  = Output(UInt(32.W))
  val testCounter = Output(UInt(32.W))
  val testCState  = Output(UInt(4.W))
  val testMState  = Output(UInt(3.W))

  val setCState   = Input(UInt(4.W))
  val setCValid   = Input(Bool())
  val setMState   = Input(UInt(3.W))
  val setMValid   = Input(Bool())
}

class AESController(addrBits: Int, beatBytes: Int)(implicit p: Parameters) extends Module { val io = IO(new AESControllerIO(addrBits, beatBytes))

  // Internal Registers
  val size_reg    = RegInit(0.U(32.W))
  val key_addr_reg    = RegInit(0.U(32.W))
  val src_addr_reg    = RegInit(0.U(32.W))
  val dest_addr_reg   = RegInit(0.U(32.W))
  val counter_reg     = RegInit(0.U(4.W))
  val mem_target_reg  = RegInit(0.U(4.W))
  val blks_remain_reg = RegInit(0.U(32.W))
  val ready_check_reg = RegInit(false.B)


  // States (C - Controller, M - Memory)
  val cState     = RegInit(AESState.sIdle)
  val cStateWire = WireDefault(cState)
  val mState      = RegInit(MemState.sIdle)
  val mStateWire = WireDefault(mState)

  // Helper Wires
  val addrWire = Wire(UInt(32.W))
  val data_wr_done = mState === MemState.sIdle
  val data_ld_done = mState === MemState.sIdle
  val enqueue_data = Reg(UInt(32.W))

  // Default DecouplerIO Signals
  io.dcplrIO.key_ready   := false.B
  io.dcplrIO.addr_ready  := false.B
  io.dcplrIO.start_ready := false.B
  io.dcplrIO.excp_ready  := true.B
  io.dcplrIO.interrupt   := false.B

  // Default DMA readReq Values
  io.dmem.readReq.valid           := false.B
  io.dmem.readReq.bits.addr       := 0.U
  io.dmem.readReq.bits.totalBytes := 0.U
  io.dmem.readResp.ready := false.B

  // Default AESCoreIO Signals
  io.aesCoreIO.we         := false.B
  io.aesCoreIO.cs         := false.B
  io.aesCoreIO.write_data := 0.U
  io.aesCoreIO.address    := 0.U


  // Default Queue Signals
  val dequeue = Module(new DMAOutputBuffer(beatBytes))
  dequeue.io.dataOut.ready := mState === MemState.sReadIntoAES
  dequeue.io.dmaInput <> io.dmem.readRespQueue

  val enqueue = Module(new DMAInputBuffer(addrBits, beatBytes))
  enqueue.io.dataIn.valid := false.B
  enqueue_data := io.aesCoreIO.read_data
  enqueue.io.baseAddr.bits := addrWire
  enqueue.io.baseAddr.valid := (mState === MemState.sWriteReq) & (counter_reg === 0.U)
  enqueue.io.dataIn.bits := enqueue_data
  io.dmem.writeReq <> enqueue.io.dmaOutput

  // Set testing signals (temporary)
  io.testCState := cState.asUInt
  io.testMState := mState.asUInt
  io.testRemain := blks_remain_reg
  io.testCounter := counter_reg
  io.testAESWriteData.valid := false.B
  io.testAESWriteData.bits := 0.U

  val testAESReadData_valid = RegInit(false.B)
  val testAESReadData_bits = RegInit(0.U(64.W))
  testAESReadData_valid := false.B
  testAESReadData_bits := 0.U
  io.testAESReadData.valid := testAESReadData_valid
  io.testAESReadData.bits := testAESReadData_bits

  io.testAESCtrl.valid := false.B
  io.testAESCtrl.bits := 0.U


  when (cState === AESState.sKeySetup) {
    addrWire := key_addr_reg
  } .elsewhen (cState === AESState.sDataSetup) {
    addrWire := src_addr_reg
  } .elsewhen (cState === AESState.sDataWrite) {
    addrWire := dest_addr_reg
  } .otherwise {
    addrWire := 0.U
  }

  // Separate state FSM w/ reset
  when (io.reset | io.dcplrIO.excp_valid) {
    cState := AESState.sIdle
    blks_remain_reg := 0.U
    counter_reg := 0.U
  } .elsewhen (io.setCValid) {
    cState := AESState(io.setCState)
  } .otherwise {
    cState := cStateWire
  }

  switch (cState) {
    is (AESState.sIdle) {
      io.dcplrIO.key_ready := true.B

      when (io.dcplrIO.key_valid) {
        // configure AES key length
        io.aesCoreIO.we := true.B
        io.aesCoreIO.cs := true.B
        io.aesCoreIO.address := AESAddr.CONFIG
        io.aesCoreIO.write_data := io.dcplrIO.key_size << 1.U
        

        // set memory addr and start memory read
        key_addr_reg := io.dcplrIO.key_addr
        when (io.dcplrIO.key_size === 0.U) {
          mem_target_reg   := 4.U
          size_reg := 16.U
          //key_size_reg := 4.U
        } .otherwise {
          mem_target_reg   := 8.U
          size_reg := 32.U
          //key_size_reg := 8.U
        }

        mStateWire := MemState.sReadReq
        cStateWire := AESState.sKeySetup
      }
    }
    is (AESState.sKeySetup) {
      // wait data loading from memory
      cStateWire := AESState.sKeySetup;
      when (data_ld_done) {
        // Start the Key Expansion Process
        io.aesCoreIO.cs := true.B
        io.aesCoreIO.we := true.B
        io.aesCoreIO.address := AESAddr.CTRL
        io.aesCoreIO.write_data := 1.U
        ready_check_reg := false.B
        cStateWire := AESState.sKeyExp;
      }
    }
    is (AESState.sKeyExp) {
      // Waiting for Key Expansion to Complete
      io.aesCoreIO.cs := 1.U
      io.aesCoreIO.address := AESAddr.STATUS
      cStateWire := AESState.sKeyExp
      // TODO: remove test signals
      io.testAESCtrl.valid := true.B
      io.testAESCtrl.bits := (AESAddr.STATUS << 32) + 0.U
      when(io.aesCoreIO.read_data(0) === ready_check_reg) {
        when (ready_check_reg === false.B) {
          ready_check_reg := true.B
        } .otherwise {
          ready_check_reg := false.B
          cStateWire := AESState.sWaitData
        }
      }
    }
    is (AESState.sWaitData) {
      // Wait for SRC and DEST address to arrive
      cStateWire := AESState.sWaitData
      io.dcplrIO.addr_ready := true.B
      when (io.dcplrIO.addr_valid) {
        // Save SRC and DEST address
        src_addr_reg := io.dcplrIO.src_addr
        dest_addr_reg := io.dcplrIO.dest_addr
        size_reg := 16.U
        mStateWire := MemState.sReadReq
        cStateWire := AESState.sDataSetup
        mem_target_reg := 4.U
      }
    }
    is (AESState.sDataSetup) {
      when (blks_remain_reg === 0.U) { // First block
        when (data_ld_done) {
          // When memory has finished loading, wait for green light
          cStateWire := AESState.sWaitStart
        } .otherwise {
          cStateWire := AESState.sDataSetup
        }
      } .otherwise {
        when (data_ld_done) {
          // When memory has finish loading, straight to processing
          cStateWire := AESState.sAESRun
        } .otherwise {
          cStateWire := AESState.sDataSetup
        }
      }
    }
    is (AESState.sWaitStart) {
      cStateWire := AESState.sWaitStart
      io.dcplrIO.start_ready := true.B
      when (io.dcplrIO.start_valid) {
        // Set ENC or DEC operation
        io.aesCoreIO.cs := true.B
        io.aesCoreIO.we := true.B
        io.aesCoreIO.address := AESAddr.CONFIG
        io.aesCoreIO.write_data := io.dcplrIO.op_type

        // set number of blocks
        blks_remain_reg := io.dcplrIO.block_count
        cStateWire := AESState.sAESRun
      }
    }
    is (AESState.sAESRun) {
      // Start the ENC/DEC process
      io.aesCoreIO.cs := true.B
      io.aesCoreIO.we := true.B
      io.aesCoreIO.address := AESAddr.CTRL
      io.aesCoreIO.write_data := 1.U << 1.U
      ready_check_reg := false.B
      cStateWire := AESState.sWaitResult
      // TODO: remove testing signals
      io.testAESCtrl.valid := true.B
      io.testAESCtrl.bits := (AESAddr.CTRL << 32) + 2.U
    }
    is (AESState.sWaitResult) {
      cStateWire := AESState.sWaitResult
      io.aesCoreIO.cs := 1.U
      io.aesCoreIO.address := AESAddr.STATUS
      when(io.aesCoreIO.read_data(0) === ready_check_reg) {
        when (ready_check_reg === false.B) {
          ready_check_reg := true.B
        } .otherwise {
          blks_remain_reg := blks_remain_reg - 1.U

          ready_check_reg := false.B
          mStateWire := MemState.sWriteIntoMem
          cStateWire := AESState.sDataWrite
        }
      }
    }
    is (AESState.sDataWrite) {
      when (mState === MemState.sWriteIntoMem) {
        // Read AES result out to DMA
        io.aesCoreIO.cs := 1.U
        io.aesCoreIO.address := AESAddr.RESULT + 3.U - counter_reg
        cStateWire := AESState.sDataWrite
      } .elsewhen (data_wr_done) {
        when (blks_remain_reg > 0.U) {
          // Return to DataSetup state to read in next block
          src_addr_reg := src_addr_reg + 16.U
          dest_addr_reg := dest_addr_reg + 16.U

          mStateWire := MemState.sReadReq
          cStateWire := AESState.sDataSetup
        } .otherwise {
          // Completed Encryption/Decryption, Raise Interrupt
          io.dcplrIO.interrupt := true.B
          cStateWire := AESState.sIdle
        }
      } .otherwise {
        cStateWire := AESState.sDataWrite
      }
    }
  }

  // Separate state FSM w/ reset
  when (io.reset | io.dcplrIO.excp_valid) {
    mState := MemState.sIdle
  } .elsewhen (io.setMValid) {
    mState := MemState(io.setMState)
  } .otherwise {
    mState := mStateWire
  }

  // Memory FSM
  switch (mState) {
    is (MemState.sIdle) {
      counter_reg := 0.U
    }
    is (MemState.sReadReq) {
      // Send Memory Read Request for Key
      mStateWire := MemState.sReadReq
      io.dmem.readReq.valid := true.B
      io.dmem.readReq.bits.addr := addrWire
      io.dmem.readReq.bits.totalBytes := size_reg
      when (io.dmem.readReq.fire()) {
        mStateWire := MemState.sReadIntoAES
      }
    }
    is (MemState.sReadIntoAES) {
      mStateWire := MemState.sReadIntoAES
      when (counter_reg === mem_target_reg) {
        // Completed Memory Read
        mStateWire := MemState.sIdle
      } .otherwise {
        when (dequeue.io.dataOut.fire()) { // When we dequeue
          io.aesCoreIO.cs := true.B
          io.aesCoreIO.we := true.B
          io.aesCoreIO.write_data := dequeue.io.dataOut.bits
          counter_reg := counter_reg + 1.U;
          // TODO: remove test signals
          io.testAESWriteData.valid := true.B
          io.testAESWriteData.bits := dequeue.io.dataOut.bits
        }
      }
      // TODO: how does key/text lay out across the memory?
      when (cState === AESState.sKeySetup) {
        io.aesCoreIO.address := AESAddr.KEY + mem_target_reg - 1.U  - counter_reg
        io.testAESWriteData.bits := ((AESAddr.KEY + mem_target_reg - 1.U  - counter_reg) << 32) + dequeue.io.dataOut.bits
      } .elsewhen (cState === AESState.sDataSetup) {
        io.aesCoreIO.address := AESAddr.TEXT + 3.U - counter_reg
        io.testAESWriteData.bits := ((AESAddr.TEXT + 3.U - counter_reg) << 32) + dequeue.io.dataOut.bits
      }
    }
    is (MemState.sWriteReq) {
      mStateWire := MemState.sWriteReq
      when (counter_reg === 4.U(4.W)) {
        // Completed Memory Write
        mStateWire := MemState.sIdle
      } .otherwise {
        // Send Write Request
        //io.dmem.writeReq.bits.addr := addrWire + 4.U * counter_reg
        //io.dmem.writeReq.bits.data := io.aesCoreIO.read_data
        enqueue.io.dataIn.valid := true.B
        when (enqueue.io.dataIn.ready === true.B) {
          // TODO: remote test signals
          testAESReadData_valid := true.B
          testAESReadData_bits := io.aesCoreIO.read_data
          mStateWire := MemState.sWriteIntoMem
          counter_reg := counter_reg + 1.U;
        }
      }
    }
    is (MemState.sWriteIntoMem) {
      mStateWire := MemState.sWriteReq;
    }
  }

  // Set static system signals
  io.dcplrIO.busy := (blks_remain_reg =/= 0.U(32.W))
  io.dcplrIO.excp_ready := true.B
  io.aesCoreIO.clk := clock
  io.aesCoreIO.reset_n := ~io.reset

}
