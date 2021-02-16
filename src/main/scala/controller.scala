class AESControllerIO extends Bundle {
  // System
  val reset       = Input(UInt(1.W))

  // RoCC Decoupler
  val excp_ready  = Output(Bool())
  val excp_valid  = Input(Bool())

  val curr_state  = Output(UInt(3.W))

  val key_ready   = Output(Bool())
  val key_valid   = Input(Bool())
  val key_addr    = Input(UInt(32.W))
  val key_size    = Input(UInt(32.W))

  val addr_ready  = Output(Bool())
  val addr_valid  = Input(Bool())
  val src_addr    = Input(UInt(32.W))
  val dest_addr   = Input(UInt(32.W))

  val start_ready = Output(Bool())
  val start_valid = Input(Bool())
  val op_type     = Input(Bool())
  val block_count = Input(UInt(32.W))

  val dmem        = HellaCacheIO

  // AES Core
  val aes_reset_n = Output(Bool())
  
  val aes_cs      = Output(Bool())
  val aes_we      = Output(Bool())

  val aes_addr    = Output(UInt(8.W))
  val aes_wr_data = Output(UInt(32.W))
  val aes_rd_data = Input(UInt(32.W))
}

class AESController extends MultiIOModule {
  val io = IO(new AESControllerIO)
}
