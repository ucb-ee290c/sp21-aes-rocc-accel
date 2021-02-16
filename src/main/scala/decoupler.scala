class RoCCDecouplerIO extends Bundle {
  // RoCC (May be RoCCCoreIO depending on CC Team)
  val rocc_io     = RoCCIO

  // Controller
  val excp_ready  = Input(Bool())
  val excp_valid  = Output(Bool())

  val curr_state  = Input(UInt(3.W))

  val key_ready   = Input(Bool())
  val key_valid   = Output(Bool())
  val key_addr    = Output(UInt(32.W))
  val key_size    = Output(UInt(32.W))

  val addr_ready  = Input(Bool())
  val addr_valid  = Output(Bool())
  val src_addr    = Output(UInt(32.W))
  val dest_addr   = Output(UInt(32.W))

  val start_ready = Input(Bool())
  val start_valid = Output(Bool())
  val op_type     = Output(Bool())
  val block_count = Output(UInt(32.W))

  val dmem        = HellaCacheIO
}

class RoCCDecoupler extends MultiIOModule {
  val io = IO(new RoCCDecouplerIO)
}