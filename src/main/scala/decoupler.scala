import chisel3._
import freechips.rocketchip.tile.RoCCIO
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.rocket.HellaCacheIO

class RoCCDecouplerIO(implicit p: Parameters) extends Bundle {
  // RoCC
  val rocc_io = new RoCCIO(0)

  // Controller
  val crtlIO  = Flipped(new DecouplerControllerIO)
  val dmem    = new HellaCacheIO
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
  val busy     = Wire(Bool())

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
  when (io.crtlIO.key_ready & io.crtlIO.key_valid) {
    key_valid_reg := false.B
  }
  when (io.crtlIO.addr_ready & io.crtlIO.addr_valid) {
    addr_valid_reg := false.B
  }
  when (io.crtlIO.start_ready & io.crtlIO.start_valid) {
    start_valid_reg := false.B
  }

  // When response fires
  when (io.rocc_io.resp.fire) {
    resp_valid_reg := false.B
  }

  // Assigning other wires/signals
  io.crtlIO.excp_valid  := excp_valid_reg
  io.crtlIO.key_valid   := key_valid_reg
  io.crtlIO.key_size    := key_size_reg
  io.crtlIO.key_addr    := key_addr_reg
  io.crtlIO.addr_valid  := addr_valid_reg
  io.crtlIO.src_addr    := src_addr_reg
  io.crtlIO.dest_addr   := dest_addr_reg
  io.crtlIO.start_valid := start_valid_reg
  io.crtlIO.op_type     := op_type_reg
  io.crtlIO.block_count := block_count_reg

  funct    := io.rocc_io.cmd.bits.inst.funct
  rs1_data := io.rocc_io.cmd.bits.rs1
  rs2_data := io.rocc_io.cmd.bits.rs2
  busy     := (start_valid_reg | io.crtlIO.busy)
  
  io.rocc_io.resp.valid     := resp_valid_reg
  io.rocc_io.resp.bits.rd   := io.rocc_io.cmd.bits.inst.rd
  io.rocc_io.resp.bits.data := busy
  io.rocc_io.busy           := busy
  io.rocc_io.interrupt      := io.crtlIO.interrupt

  io.dmem := io.rocc_io.mem
}
