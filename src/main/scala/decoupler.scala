import chisel3._
import freechips.rocketchip.tile.RoCCIO
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.rocket.HellaCacheIO

class RoCCDecouplerIO(implicit p: Parameters) extends Bundle {
  // RoCC
  val rocc_io     = new RoCCIO(0)

  // Controller
  val excp_ready  = Input(Bool())
  val excp_valid  = Output(Bool())
  val interrupt   = Input(Bool())

  val busy        = Input(Bool())

  val key_ready   = Input(Bool())
  val key_valid   = Output(Bool())
  val key_size    = Output(UInt(1.W))
  val key_addr    = Output(UInt(32.W))

  val addr_ready  = Input(Bool())
  val addr_valid  = Output(Bool())
  val src_addr    = Output(UInt(32.W))
  val dest_addr   = Output(UInt(32.W))

  val start_ready = Input(Bool())
  val start_valid = Output(Bool())
  val op_type     = Output(UInt(1.W))
  val block_count = Output(UInt(32.W))

  val dmem          = new HellaCacheIO
}

class RoCCDecoupler(implicit p: Parameters) extends MultiIOModule {
  // Internal Registers
  val excp_valid_reg  = RegInit(false.B)
  val key_valid_reg   = RegInit(false.B)
  val key_size_reg    = RegInit(0.U(1.W))
  val key_addr_reg    = RegInit(0.U(32.W))
  val addr_valid_reg  = RegInit(false.B)
  val src_addr_reg    = RegInit(0.U(32.W))
  val dest_addr_reg   = RegInit(0.U(32.W))
  val start_valid_reg = RegInit(false.B)
  val op_type_reg     = RegInit(0.U(1.W))
  val block_count_reg = RegInit(0.U(32.W))
  val resp_valid_reg  = RegInit(false.B)

  // Helper wires
  val funct    = Wire(UInt(7.W))
  val rs1_data = Wire(UInt(32.W))
  val rs2_data = Wire(UInt(32.W))

  // IO
  val io = IO(new RoCCDecouplerIO)

  // Unwrapping RoCCCommands
  when (io.rocc_io.cmd.fire) {
    when (funct === 0.U(7.W)) {
      key_valid_reg := true.B
      key_size_reg  := 0.U
      key_addr_reg  := rs1_data
    } .elsewhen (funct === 1.U(7.W)) {
      key_valid_reg := true.B
      key_size_reg  := 1.U
      key_addr_reg  := rs1_data
    } .elsewhen (funct === 2.U(7.W)) {
      addr_valid_reg := true.B
      src_addr_reg   := rs1_data
      dest_addr_reg  := rs2_data
    } .elsewhen (funct === 3.U(7.W)) {
      start_valid_reg := true.B
      op_type_reg     := 0.U(1.W)
      block_count_reg := rs1_data
    } .elsewhen (funct === 4.U(7.W)) {
      start_valid_reg := true.B
      op_type_reg     := 1.U(1.W)
      block_count_reg := rs1_data
    } 
    // Separate when statement
    when (funct === 5.U(7.W)) {
      resp_valid_reg := true.B
    }
  }

  // When register groups "fire" (ready && valid high)
  when (io.key_ready & io.key_valid) {
    key_valid_reg := false.B
  }
  when (io.addr_ready & io.addr_valid) {
    addr_valid_reg := false.B
  }
  when (io.start_ready & io.start_valid) {
    start_valid_reg := false.B
  }

  // When response fires
  when (io.rocc_io.resp.fire) {
    resp_valid_reg := false.B
  }

  // Assigning other wires/signals
  io.excp_valid  := excp_valid_reg
  io.key_valid   := key_valid_reg
  io.key_size    := key_size_reg
  io.key_addr    := key_addr_reg
  io.addr_valid  := addr_valid_reg
  io.src_addr    := src_addr_reg
  io.dest_addr   := dest_addr_reg
  io.start_valid := start_valid_reg
  io.op_type     := op_type_reg
  io.block_count := block_count_reg

  funct    := io.rocc_io.cmd.bits.inst.funct
  rs1_data := io.rocc_io.cmd.bits.rs1
  rs2_data := io.rocc_io.cmd.bits.rs2
  
  io.rocc_io.resp.valid     := resp_valid_reg
  io.rocc_io.resp.bits.rd   := io.rocc_io.cmd.bits.inst.rd
  io.rocc_io.resp.bits.data := io.busy
  io.rocc_io.busy           := io.busy
  io.rocc_io.interrupt      := io.interrupt

  io.dmem := io.rocc_io.mem
}
