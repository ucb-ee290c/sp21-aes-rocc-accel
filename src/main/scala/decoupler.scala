import chisel3._
import chisel3.util.Decoupled
import freechips.rocketchip.tile.RoCCCoreIO
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.tile.RoCCCommand
import freechips.rocketchip.tile.RoCCResponse

class RoCCDecouplerIO(implicit p: Parameters) extends Bundle {
  // RoCCCommand + Other Signals
  val rocc_cmd  = Flipped(Decoupled(new RoCCCommand))
  val rocc_resp = Decoupled(new RoCCResponse)
  val rocc_busy = Output(Bool())
  val rocc_intr = Output(Bool())
  val rocc_excp = Input(Bool())

  // Controller
  val ctrlIO  = Flipped(new DecouplerControllerIO)
}

class RoCCDecoupler(implicit p: Parameters) extends Module {
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
  val busy     = Wire(Bool())

  // IO
  val io = IO(new RoCCDecouplerIO)

  // Unwrapping RoCCCommands
  when (io.rocc_cmd.fire) {
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
  when (io.ctrlIO.key_ready & io.ctrlIO.key_valid) {
    key_valid_reg := false.B
  }
  when (io.ctrlIO.addr_ready & io.ctrlIO.addr_valid) {
    addr_valid_reg := false.B
  }
  when (io.ctrlIO.start_ready & io.ctrlIO.start_valid) {
    start_valid_reg := false.B
  }

  // When response fires
  when (io.rocc_resp.fire) {
    resp_valid_reg := false.B
  }

  // Assigning other wires/signals
  io.ctrlIO.excp_valid  := excp_valid_reg
  io.ctrlIO.key_valid   := key_valid_reg
  io.ctrlIO.key_size    := key_size_reg
  io.ctrlIO.key_addr    := key_addr_reg
  io.ctrlIO.addr_valid  := addr_valid_reg
  io.ctrlIO.src_addr    := src_addr_reg
  io.ctrlIO.dest_addr   := dest_addr_reg
  io.ctrlIO.start_valid := start_valid_reg
  io.ctrlIO.op_type     := op_type_reg
  io.ctrlIO.block_count := block_count_reg

  funct    := io.rocc_cmd.bits.inst.funct
  rs1_data := io.rocc_cmd.bits.rs1
  rs2_data := io.rocc_cmd.bits.rs2
  busy     := (start_valid_reg | io.ctrlIO.busy)
  
  io.rocc_cmd.ready      := ~busy
  io.rocc_resp.valid     := resp_valid_reg
  io.rocc_resp.bits.rd   := io.rocc_cmd.bits.inst.rd
  io.rocc_resp.bits.data := busy
  io.rocc_busy           := busy
  io.rocc_intr           := io.ctrlIO.interrupt
}
