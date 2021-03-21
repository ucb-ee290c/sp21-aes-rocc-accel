package aes

import chisel3._
import chisel3.util._
import ee290cdma.EE290CDMAWriterReq

// Takes 32bit inputs (from AES core) and writes to DMA
class DMAInputBuffer (addrBits: Int = 32, beatBytes: Int) extends Module {
  val io = IO(new Bundle {
    val baseAddr = Flipped(Decoupled(UInt(addrBits.W)))
    val dataIn  = Flipped(Decoupled(UInt(32.W)))
    val dmaOutput = Decoupled(new EE290CDMAWriterReq(addrBits, beatBytes))
  })

  val bitsFilled = RegInit(0.U(log2Ceil(128 + 1).W))
  val addrReg = RegInit(0.U(addrBits.W))
  val wideData = RegInit(0.U(128.W)) // AES data block size is 128b
  // AES core outputs 32 bits at a time
  val dataQueue = Module(new Queue(UInt(32.W), 4))
  // Signal when to start writing to DMA (ensure that all 128b of data is correctly matched to address)
  val startWrite = RegInit(false.B)

  // Start writing when we have an entire block of data (128b)
  when (bitsFilled === 128.U) {
    startWrite := true.B
  } .elsewhen (bitsFilled === 0.U) {
    startWrite := false.B
  }

  // NOTE: Base addr will only be set once per block in memory controller FSM
  when (io.baseAddr.fire()) {
    addrReg := io.baseAddr.bits
  }
  // NOTE: The two statements below will NEVER concurrently fire (fire conditions prevent)
  when (dataQueue.io.deq.fire()) {
    wideData := wideData | (dataQueue.io.deq.bits << bitsFilled).asUInt()
    bitsFilled := bitsFilled + 32.U
  }
  when (io.dmaOutput.fire()) {
    addrReg := addrReg + io.dmaOutput.bits.totalBytes
    wideData := wideData >> (io.dmaOutput.bits.totalBytes * 8.U)
    bitsFilled := bitsFilled - (io.dmaOutput.bits.totalBytes * 8.U)
  }

  io.baseAddr.ready := ~startWrite
  dataQueue.io.deq.ready := ~startWrite
  dataQueue.io.enq <> io.dataIn
  io.dmaOutput.valid := (bitsFilled > 0.U) & startWrite
  io.dmaOutput.bits.addr := addrReg
  io.dmaOutput.bits.data := wideData
  when (beatBytes.U < 16.U) {
    io.dmaOutput.bits.totalBytes := beatBytes.U
  } .otherwise {
    // 16 bytes == 128 bits
    io.dmaOutput.bits.totalBytes := 16.U
  }
}

// Outputs data from DMA in 32bit chunks (for AES core)
class DMAOutputBuffer (beatBytes: Int) extends Module {
  val io = IO(new Bundle {
    val dmaInput  = Flipped(Decoupled(UInt((beatBytes*8).W)))
    val dataOut = Decoupled(UInt(32.W))
  })

  val bitsFilled = RegInit(0.U(log2Ceil(256 + 1).W))
  val wideData = RegInit(0.U(256.W)) // Max data we will ever read at a time is 256b
  val dataQueue = Module(new Queue(UInt(32.W), 8))

  // NOTE: These two statements will NEVER concurrently fire (fire conditions prevent)
  when (dataQueue.io.enq.fire()) {
    bitsFilled := bitsFilled - 32.U
    wideData := wideData >> 32
  }
  when (io.dmaInput.fire()) {
    bitsFilled := bitsFilled + (beatBytes * 8).U
    wideData := wideData | (io.dmaInput.bits << bitsFilled).asUInt()
  }

  io.dmaInput.ready := bitsFilled < 32.U
  io.dataOut <> dataQueue.io.deq

  dataQueue.io.enq.valid := bitsFilled >= 32.U
  dataQueue.io.enq.bits := wideData(31,0)
}
