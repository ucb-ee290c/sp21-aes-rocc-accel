class AESIO extends Bundle {
  val clk        = Input(Clock())
  val reset_n    = Input(Bool())
  val cs         = Input(Bool())
  val we         = Input(Bool())
  val address    = Input(UInt(8.W))
  val write_data = Input(UInt(32.W))
  val read_data  = Output(UInt(32.W))
}

class AESBlackBox(implicit p: Parameters) extends BlackBox with HasBlackBoxResource {
  val io = IO(new AESIO)

  addResource("/vsrc/aes.v")
}
