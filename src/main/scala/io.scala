import chisel3._

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

class MemoryIO extends Bundle {
  val mem_req_valid = Input(Bool())
  val mem_req_ready = Output(Bool())
  val mem_req_cmd = Output(UInt(5.W))
  val mem_req_size = Output(UInt(3.W))
  val mem_req_addr = Output(UInt(32.W))
  val mem_req_data = Output(UInt(32.W))

  val mem_resp_valid = Input(Bool())
  val mem_resp_data = Input(UInt(32.W))
}
