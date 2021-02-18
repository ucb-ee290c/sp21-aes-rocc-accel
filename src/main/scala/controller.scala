package aes
import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.rocket.HellaCacheIO

class AESControllerIO(implicit p: Parameters) extends Bundle {
  // System
  val reset       = Input(UInt(1.W))

  // RoCC Decoupler
  val excp_ready  = Output(Bool())
  val excp_valid  = Input(Bool())
  val interrupt   = Output(Bool())

  val busy        = Output(Bool())

  // val curr_state  = Output(UInt(4.W))

  val key_ready   = Output(Bool())
  val key_valid   = Input(Bool())
  val key_addr    = Input(UInt(32.W))
  val key_size    = Input(UInt(1.W))

  val addr_ready  = Output(Bool())
  val addr_valid  = Input(Bool())
  val src_addr    = Input(UInt(32.W))
  val dest_addr   = Input(UInt(32.W))

  val start_ready = Output(Bool())
  val start_valid = Input(Bool())
  val op_type     = Input(Bool())
  val block_count = Input(UInt(32.W))

  val dmem        = new HellaCacheIO
  //val decoupler_io = new Flipped(RoCCDecouplerIO)

  // AES Core
  val aes_reset_n = Output(Bool())
  
  val aes_cs      = Output(Bool())
  val aes_we      = Output(Bool())

  val aes_addr    = Output(UInt(8.W))
  val aes_wr_data = Output(UInt(32.W))
  val aes_rd_data = Input(UInt(32.W))
  //val aes_io = new AESIO
}

class AESController(implicit p: Parameters) extends MultiIOModule {
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
  object AESState extends ChiselEnum {
    val sIdle, sKeySetup, sKeyExp, sWaitData, sDataSetup, sWaitStart, sAESRun, sWaitResult, sDataWrite = Value
  }

  val cState = RegInit(AESState.sIdle)

  // default setting
  io.key_ready := 0.U
  io.addr_ready := 0.U
  io.start_ready := 0.U
  io.excp_ready := 1.U
  io.aes_we := false.B
  io.aes_cs := false.B
  io.interrupt := false.B

  when (cState === AESState.sKeySetup) {
    addr := key_addr_reg
  } .elsewhen (cState === AESState.sDataSetup) {
    addr := src_addr_reg
  } .elsewhen (cState === AESState.sDataWrite) {
    addr := dest_addr_reg
  }

  // set busy signal if controller is not in Idle state
  when (cState !== AESState.sIdle) {
    io.busy := true.B
  }
  

  switch (cState) {
    is (AESState.sIdle) {
      io.key_ready := 1.U
      when (io.key_valid) {

        // configure AES key length
        io.aes_we := true.B
        io.aes_cs := true.B
        io.aes_addr := AESAddr.CONFIG
        io.aes_wr_data  := io.key_size << 1.U
        
        // set memory addr and start memory read
        key_addr_reg := io.key_addr
        when (io.key_size === 0.U) {
          target_reg := 4.U
        } .otherwise {
          target_reg := 8.U
        }

        mState := MemState.sReadAddr
        cState := AESState.sKeySetup
      }
    }
    is (AESState.sKeySetup) {
      // wait data loading from memory
      when (mState === MemState.sIdle) {
        // set key init
        io.aes_cs := 1.U
        io.aes_we := 1.U
        io.aes_addr  := AESAddr.CTRL
        io.aes_wr_data := 1.U
        when(io.aes_rd_data(0) === 0.U) {
          cState := AESState.sKeyExp;
        }
      }
    }
    is (AESState.sKeyExp) {
      // wait aes key expansion
      io.aes_cs := 1.U
      io.aes_addr := AESAddr.STATUS
      when(io.aes_rd_data(0) === 1.U) {
        cState := AESState.sWaitData
      }
    }
    is (AESState.sWaitData) {
      io.addr_ready := 1.U
      when (io.addr_valid) {
        // set memory addr and start memory read
        target_reg := 4.U
        src_addr_reg := io.src_addr
        dest_addr_reg := io.dest_addr

        mState := MemState.sWriteAddr
        cState := AESState.sDataSetup
      }
    }
    is (AESState.sDataSetup) {
      // wait data loading from memory
      when (mState === MemState.sIdle && remain_reg === 0.U) {
        cState := AESState.sWaitStart
      } .otherwise {
        cState := AESState.sAESRun
      }
    }
    is (AESState.sWaitStart) {
      io.start_ready := 1.U
      when (io.start_valid) {
        // set enc/dec mode
        io.aes_cs := 1.U
        io.aes_we := 1.U
        io.aes_addr := AESAddr.CONFIG
        io.aes_wr_data := io.op_type

        // set number of blocks
        remain_reg := io.block_count
      }
    }
    is (AESState.sAESRun) {
      // set aes control NEXT
      io.aes_cs := 1.U
      io.aes_we := 1.U
      io.aes_addr := AESAddr.CTRL
      io.aes_wr_data := 1.U << 1.U
      when(io.aes_rd_data(0) === 0.U) {
        cState := AESState.sWaitResult
      }
    }
    is (AESState.sWaitResult) {
      io.aes_cs := 1.U
      io.aes_addr := AESAddr.STATUS
      when(io.aes_rd_data(0) === 1.U) {
        addr := io.dest_addr
        target_reg := 4.U
        remain_reg := remain_reg - 1.U

        mState := MemState.sWrite
        cState := AESState.sDataWrite
      }
    }
    is (AESState.sDataWrite) {
      when (mState === MemState.sWrite) {
        // read aes result 
        io.aes_cs := 1.U
        io.aes_addr := AESAddr.RESULT + counter_reg
      } .elsewhen (mState === MemState.sIdle) {
        when (remain_reg > 0.U) {
          // go back to DataSetup to read next text
          src_addr_reg := src_addr_reg + 4.U
          dest_addr_reg := dest_addr_reg + 4.U
          cState := AESState.sDataSetup
        } .elsewhen (remain_reg === 0.U) {
          // done encryption/decryption, set interrupt
          cState := AESState.sIdle
          io.interrupt := true.B
        }
      }
    }
  }

  // Memory Controller
  object MemState extends ChiselEnum {
    val sIdle, sReadAddr, sRead, sWriteAddr, sWrite = Value
  }
  val mState = RegInit(MemState.sIdle)

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
        io.aes_cs := 1.U
        io.aes_we := 1.U
        io.aes_wr_data := io.dmem.resp.bits.data 
        when (cState === AESState.sKeySetup) {
          io.aes_addr := AESAddr.KEY + counter_reg
        } .otherwise {
          io.aes_addr := AESAddr.TEXT + counter_reg
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
          io.dmem.req.bits.data := io.aes_rd_data
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
