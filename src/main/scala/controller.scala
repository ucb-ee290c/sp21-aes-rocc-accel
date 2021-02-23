import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.rocket.HellaCacheIO

class AESControllerIO(implicit p: Parameters) extends Bundle {
  // System
  val reset       = Input(Bool())

  // RoCC Decoupler
  val dcplrIO     = new DecouplerControllerIO
  val dmem        = new HellaCacheIO

  // AES Core
  val aesCoreIO   = Flipped(new AESCoreIO)
}

class AESController(implicit p: Parameters) extends Module {
  val io = IO(new AESControllerIO) 


  // Internal Registers
  val key_addr_reg = RegInit(0.U(32.W))
  val src_addr_reg = RegInit(0.U(32.W))
  val dest_addr_reg = RegInit(0.U(32.W))
  val counter_reg = RegInit(0.U(3.W))
  val target_reg = RegInit(0.U(3.W))
  val remain_reg = RegInit(0.U(32.W))

  // Helper Wires
  val addr = Wire(UInt(32.W))

  // Set system signals
  io.dcplrIO.busy := (remain_reg =/= 0.U(32.W))
  io.aesCoreIO.reset_n := ~io.reset

  // Set AES State Variables
  object AESState extends ChiselEnum {
    val sIdle, sKeySetup, sKeyExp, sWaitData, sDataSetup, sWaitStart, sAESRun, sWaitResult, sDataWrite = Value
  }
  val cState = RegInit(AESState.sIdle)
  val cStateWire = WireDefault(cState)

  // Set Memory State Variables
  object MemState extends ChiselEnum {
    val sIdle, sReadAddr, sRead, sWriteAddr, sWrite = Value
  }
  val mState = RegInit(MemState.sIdle)
  
  /* AES Controller */
  
  // AES address map
  object AESAddr {
    val CTRL = 8.U(8.W)
    val STATUS = 9.U(8.W)
    val CONFIG = 10.U(8.W)
    val KEY = 16.U(8.W)
    val TEXT = 32.U(8.W)
    val RESULT = 48.U(8.W)
  }

  // default setting for dcpolrIO and aesCoreIO
  io.dcplrIO.key_ready := false.B
  io.dcplrIO.addr_ready := false.B
  io.dcplrIO.start_ready := false.B
  io.dcplrIO.excp_ready := true.B
  io.aesCoreIO.we := false.B
  io.aesCoreIO.cs := false.B
  io.aesCoreIO.clk := clock
  io.aesCoreIO.write_data := 0.U
  io.aesCoreIO.address := 0.U
  io.dcplrIO.interrupt := false.B

  addr := 0.U

  when (cState === AESState.sKeySetup) {
    addr := key_addr_reg
  } .elsewhen (cState === AESState.sDataSetup) {
    addr := src_addr_reg
  } .elsewhen (cState === AESState.sDataWrite) {
    addr := dest_addr_reg
  }

  // Separate state FSM w/ reset
  when (io.reset | io.dcplrIO.excp_valid) {
    cState := AESState.sIdle
  } .otherwise {
    cState := cStateWire
  }
  

  switch (cState) {
    is (AESState.sIdle) {
      io.dcplrIO.key_ready := 1.U
      io.dcplrIO.interrupt := false.B

      when (io.dcplrIO.key_valid) {
        // configure AES key length
        io.aesCoreIO.we := true.B
        io.aesCoreIO.cs := true.B
        io.aesCoreIO.address := AESAddr.CONFIG
        io.aesCoreIO.write_data  := io.dcplrIO.key_size << 1.U
        
        // set memory addr and start memory read
        key_addr_reg := io.dcplrIO.key_addr
        when (io.dcplrIO.key_size === 0.U) {
          target_reg := 4.U
        } .otherwise {
          target_reg := 8.U
        }

        mState := MemState.sReadAddr
        cStateWire := AESState.sKeySetup
      }
    }
    is (AESState.sKeySetup) {
      // wait data loading from memory
      when (mState === MemState.sIdle) {
        // set key init
        io.aesCoreIO.cs := 1.U
        io.aesCoreIO.we := 1.U
        io.aesCoreIO.address  := AESAddr.CTRL
        io.aesCoreIO.write_data := 1.U
        when(io.aesCoreIO.read_data(0) === 0.U) {
          cStateWire := AESState.sKeyExp;
        }
      }
    }
    is (AESState.sKeyExp) {
      // wait aes key expansion
      io.aesCoreIO.cs := 1.U
      io.aesCoreIO.address := AESAddr.STATUS
      when(io.aesCoreIO.read_data(0) === 1.U) {
        cStateWire := AESState.sWaitData
      }
    }
    is (AESState.sWaitData) {
      io.dcplrIO.addr_ready := 1.U
      when (io.dcplrIO.addr_valid) {
        // set memory addr and start memory read
        target_reg := 4.U
        src_addr_reg := io.dcplrIO.src_addr
        dest_addr_reg := io.dcplrIO.dest_addr

        mState := MemState.sWriteAddr
        cStateWire := AESState.sDataSetup
      }
    }
    is (AESState.sDataSetup) {
      // wait data loading from memory
      when (mState === MemState.sIdle && remain_reg === 0.U) {
        cStateWire := AESState.sWaitStart
      } .otherwise {
        cStateWire := AESState.sAESRun
      }
    }
    is (AESState.sWaitStart) {
      io.dcplrIO.start_ready := 1.U
      when (io.dcplrIO.start_valid) {
        // set enc/dec mode
        io.aesCoreIO.cs := 1.U
        io.aesCoreIO.we := 1.U
        io.aesCoreIO.address := AESAddr.CONFIG
        io.aesCoreIO.write_data := io.dcplrIO.op_type

        // set number of blocks
        remain_reg := io.dcplrIO.block_count
      }
    }
    is (AESState.sAESRun) {
      // set aes control NEXT
      io.aesCoreIO.cs := 1.U
      io.aesCoreIO.we := 1.U
      io.aesCoreIO.address := AESAddr.CTRL
      io.aesCoreIO.write_data := 1.U << 1.U
      when(io.aesCoreIO.read_data(0) === 0.U) {
        cStateWire := AESState.sWaitResult
      }
    }
    is (AESState.sWaitResult) {
      io.aesCoreIO.cs := 1.U
      io.aesCoreIO.address := AESAddr.STATUS
      when(io.aesCoreIO.read_data(0) === 1.U) {
        addr := io.dcplrIO.dest_addr
        target_reg := 4.U
        remain_reg := remain_reg - 1.U

        mState := MemState.sWrite
        cStateWire := AESState.sDataWrite
      }
    }
    is (AESState.sDataWrite) {
      when (mState === MemState.sWrite) {
        // read aes result 
        io.aesCoreIO.cs := 1.U
        io.aesCoreIO.address := AESAddr.RESULT + counter_reg
      } .elsewhen (mState === MemState.sIdle) {
        when (remain_reg > 0.U) {
          // go back to DataSetup to read next text
          src_addr_reg := src_addr_reg + 4.U
          dest_addr_reg := dest_addr_reg + 4.U
          cStateWire := AESState.sDataSetup
        } .elsewhen (remain_reg === 0.U) {
          // done encryption/decryption, set interrupt
          cStateWire := AESState.sIdle
          io.dcplrIO.interrupt := true.B
        }
      }
    }
  }

  // Memory Controller

  // TODO: Optimize by sending 4 concurrent requests (NEED TO HANDLE REORDERING WITH TAG)

  // TODO: Remove later
  io.dmem.keep_clock_enabled := false.B
  io.dmem.req.bits.tag := 0.U
  io.dmem.req.bits.phys := 0.U
  io.dmem.s1_data.data := 0.U
  io.dmem.req.bits.signed := 0.U
  io.dmem.req.bits.addr := 0.U
  io.dmem.s2_kill := false.B
  io.dmem.req.bits.data := 0.U
  io.dmem.req.bits.mask := 0.U
  io.dmem.req.bits.cmd := 0.U
  io.dmem.req.bits.no_xcpt := false.B
  io.dmem.req.valid := 0.U
  io.dmem.s1_kill := false.B
  io.dmem.s1_data.mask := 0.U
  io.dmem.req.bits.size := 0.U
  io.dmem.req.bits.no_alloc := 0.U
  
  switch (mState) {
    is (MemState.sIdle) {
      counter_reg := 0.U;
    }
    is (MemState.sReadAddr) {
      when (counter_reg === target_reg) {
        // memory read done
        mState := MemState.sIdle
      } .otherwise {
        // set memory read address
        io.dmem.req.valid := 1.U
        when (io.dmem.req.ready) {
          io.dmem.req.bits.addr := addr + 4.U * counter_reg
          io.dmem.req.bits.cmd := 0.U
          io.dmem.req.bits.size := 2.U
          mState := MemState.sRead
        }
      }
    }
    is (MemState.sRead) {
      when (io.dmem.resp.valid) { // memory responds data
        io.aesCoreIO.cs := 1.U
        io.aesCoreIO.we := 1.U
        io.aesCoreIO.write_data := io.dmem.resp.bits.data 
        when (cState === AESState.sKeySetup) {
          io.aesCoreIO.address := AESAddr.KEY + counter_reg
        } .otherwise {
          io.aesCoreIO.address := AESAddr.TEXT + counter_reg
        }
        counter_reg := counter_reg + 1.U;
        mState := MemState.sReadAddr
      }
    }
    is (MemState.sWriteAddr) {
      when (counter_reg === target_reg) {
        // memory write done
        mState := MemState.sIdle
      } .otherwise {
        // write data to memory
        io.dmem.req.valid := 1.U
        when (io.dmem.req.ready) {
          io.dmem.req.bits.addr := addr + 4.U * counter_reg
          io.dmem.req.bits.cmd := 1.U
          io.dmem.req.bits.size := 2.U
          io.dmem.req.bits.data := io.aesCoreIO.read_data
          mState := MemState.sWrite
          counter_reg := counter_reg + 1.U;
        }
      }
    }
    is (MemState.sWrite) {
      mState := MemState.sWriteAddr;
    }
  }
}
