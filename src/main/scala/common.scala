package aes

import chisel3._
import chisel3.util.Decoupled
import chipsalliance.rocketchip.config.Parameters
import chisel3.experimental.ChiselEnum
import ee290cdma.{EE290CDMAReaderReq, EE290CDMAReaderResp, EE290CDMAWriterReq}

// Common Interfaces

class AESCoreIO extends Bundle {
  val clk        = Input(Clock())
  val reset_n    = Input(Bool())
  val cs         = Input(Bool())
  val we         = Input(Bool())
  val address    = Input(UInt(8.W))
  val write_data = Input(UInt(32.W))
  val read_data  = Output(UInt(32.W))
}

class DecouplerControllerIO extends Bundle {
  val excp_ready  = Output(Bool())
  val excp_valid  = Input(Bool())
  val interrupt   = Output(Bool())
  val busy        = Output(Bool())

  val key_ready   = Output(Bool())
  val key_valid   = Input(Bool())
  val key_size    = Input(UInt(1.W))
  val key_addr    = Input(UInt(32.W))

  val addr_ready  = Output(Bool())
  val addr_valid  = Input(Bool())
  val src_addr    = Input(UInt(32.W))
  val dest_addr   = Input(UInt(32.W))

  val start_ready = Output(Bool())
  val start_valid = Input(Bool())
  val op_type     = Input(Bool())
  val block_count = Input(UInt(32.W))
  val intrpt_en   = Input(Bool())
}

class ControllerDMAIO (addrBits: Int, beatBytes: Int)(implicit p: Parameters) extends Bundle {
  val writeReq       = Decoupled(new EE290CDMAWriterReq(addrBits, beatBytes))
  val readReq        = Decoupled(new EE290CDMAReaderReq(addrBits, 256))  // Hardcoded due to 256b key and 128b blocks
  val readResp       = Flipped(Decoupled(new EE290CDMAReaderResp(256)))
  val readRespQueue  = Flipped(Decoupled(UInt((beatBytes * 8).W)))
  val busy           = Input(Bool())
}

object AESState extends ChiselEnum {
  val sIdle, sKeySetup, sKeyExp, sWaitData, sDataSetup, sWaitStart, sAESRun, sWaitResult, sDataWrite = Value
}

object MemState extends ChiselEnum {
  val sIdle, sReadReq, sReadIntoAES, sWriteReq, sWriteIntoMem = Value
}

// AES address map
object AESAddr {
  val CTRL = 8.U(8.W)
  val STATUS = 9.U(8.W)
  val CONFIG = 10.U(8.W)
  val KEY = 16.U(8.W)
  val TEXT = 32.U(8.W)
  val RESULT = 48.U(8.W)
}
