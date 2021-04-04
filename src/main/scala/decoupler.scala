package aes

import chisel3._
import chisel3.util.{Decoupled, Queue}
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.tile.RoCCCommand
import freechips.rocketchip.tile.RoCCResponse

class RoCCDecouplerIO(implicit p: Parameters) extends Bundle {
  // System Signal
  val reset     = Input(Bool())

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
  val intrpt_en_reg   = RegInit(0.U(1.W))
  val resp_rd_reg     = RegInit(0.U(5.W))
  val resp_data_reg   = RegInit(0.U(32.W))
  val resp_valid_reg  = RegInit(false.B)
  val ctrl_busy_reg   = RegInit(false.B)


  // Helper wires
  val reset_wire = Wire(Bool())
  val funct      = Wire(UInt(7.W))
  val rs1_data   = Wire(UInt(32.W))
  val rs2_data   = Wire(UInt(32.W))
  val rd         = Wire(UInt(5.W))
  val busy       = Wire(Bool())

  // IO
  val io = IO(new RoCCDecouplerIO)

  // Unwrapping RoCCCommands
  // Does nothing when reset
  when (io.rocc_cmd.fire & ~reset_wire) {
    when (funct === 0.U(7.W)) {
      key_valid_reg := true.B
      key_size_reg  := 0.U
      key_addr_reg  := rs1_data
    } .elsewhen ((funct === 1.U(7.W)) & ~key_valid_reg) { // Cannot overwrite valid (edgecase where ctrl reads at same time)
      key_valid_reg := true.B
      key_size_reg  := 1.U
      key_addr_reg  := rs1_data
    } .elsewhen ((funct === 2.U(7.W)) & ~addr_valid_reg) {
      addr_valid_reg := true.B
      src_addr_reg   := rs1_data
      dest_addr_reg  := rs2_data
    } .elsewhen ((funct === 3.U(7.W)) & ~start_valid_reg) {
      start_valid_reg := true.B
      op_type_reg     := 1.U(1.W)
      block_count_reg := rs1_data
      intrpt_en_reg   := rs2_data(0)
    } .elsewhen ((funct === 4.U(7.W)) & ~start_valid_reg) {
      start_valid_reg := true.B
      op_type_reg     := 0.U(1.W)
      block_count_reg := rs1_data
      intrpt_en_reg   := rs2_data(0)
    } .elsewhen ((funct === 5.U(7.W)) & ~resp_valid_reg) {
      resp_rd_reg    := rd
      resp_data_reg  := busy
      resp_valid_reg := true.B
    } .elsewhen ((funct === 6.U(7.W)) & ~resp_valid_reg) {
      resp_rd_reg    := rd
      resp_data_reg  := 1.U(32.W)
      // Note: currently accelerator only has 1 interrupt (when enc/dec ends)
      // !!! ASSUMES THAT CPU WILL ONLY QUERY INTERRUPT STATUS AFTER OBSERVING AN INTERRUPT
      resp_valid_reg := true.B
    }
  }

  // When an exception is received (only ignored when io.reset is high)
  when (io.rocc_excp & ~io.reset) {
    excp_valid_reg := true.B
  }

  // When register groups "fire" (ready && valid high)
  // Clear all valid when reset
  when ((io.ctrlIO.key_ready & io.ctrlIO.key_valid) | reset_wire) {
    key_valid_reg := false.B
  }
  when ((io.ctrlIO.addr_ready & io.ctrlIO.addr_valid) | reset_wire) {
    addr_valid_reg := false.B
  }
  when ((io.ctrlIO.start_ready & io.ctrlIO.start_valid) | reset_wire) {
    start_valid_reg := false.B
  }
  // Only ignored when io.reset is high
  when ((io.ctrlIO.excp_ready & io.ctrlIO.excp_valid) | io.reset) {
    excp_valid_reg := false.B
  }

  // When response fires
  // Clear valid when reset
  when (io.rocc_resp.fire | reset_wire) {
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
  io.ctrlIO.intrpt_en   := intrpt_en_reg

  reset_wire    := io.rocc_excp | io.reset
  funct         := io.rocc_cmd.bits.inst.funct
  rs1_data      := io.rocc_cmd.bits.rs1
  rs2_data      := io.rocc_cmd.bits.rs2
  rd            := io.rocc_cmd.bits.inst.rd
  ctrl_busy_reg := io.ctrlIO.busy
  busy          := (start_valid_reg | ctrl_busy_reg)

  // Should be always ready to process instructions
  io.rocc_cmd.ready      := true.B
  io.rocc_resp.valid     := resp_valid_reg
  io.rocc_resp.bits.rd   := resp_rd_reg
  io.rocc_resp.bits.data := resp_data_reg
  io.rocc_busy           := busy
  io.rocc_intr           := io.ctrlIO.interrupt
}
