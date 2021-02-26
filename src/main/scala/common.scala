import chisel3._
import chisel3.util.Decoupled
import chisel3.util.Valid
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.rocket.HellaCacheReq
import freechips.rocketchip.rocket.HellaCacheResp
import chisel3.experimental.ChiselEnum

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
}

class MemoryIO (implicit p: Parameters) extends Bundle {
  val req = Decoupled(new HellaCacheReq)
  val resp = Flipped(Valid(new HellaCacheResp))
}

object AESState extends ChiselEnum {
    val sIdle, sKeySetup, sKeyExp, sWaitData, sDataSetup, sWaitStart, sAESRun, sWaitResult, sDataWrite = Value
  }

object MemState extends ChiselEnum {
    val sIdle, sReadAddr, sRead, sWriteAddr, sWrite = Value
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
