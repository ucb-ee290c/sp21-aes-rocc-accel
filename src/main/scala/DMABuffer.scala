package aes

import chisel3._
import chisel3.util._
import ee290cdma.EE290CDMAWriterReq

// Takes 32bit inputs (from AES core) and writes to DMA
class DMAInputBuffer (addrBits: Int = 32, beatBytes: Int) extends Module {
  val io = IO(new Bundle {
    val dataIn  = Flipped(Decoupled(UInt((addrBits + 32).W)))
    val dmaOutput = Decoupled(new EE290CDMAWriterReq(addrBits, beatBytes))
  })

  val bitsFilled = RegInit(0.U(log2Ceil(32 + 1).W))
  val addrReg = RegInit(0.U(addrBits.W))
  val wideData = RegInit(0.U(32.W)) // Max data we will ever write at a time is 32b
  // Can fill up beatBytes, but not necessary
  val dataQueue = Module(new Queue(UInt((addrBits + 32).W), 4))

  // NOTE: These two statements will NEVER concurrently fire (fire conditions prevent)
  when (dataQueue.io.deq.fire()) {
    addrReg := dataQueue.io.deq.bits(32 + addrBits - 1, 32)
    wideData := dataQueue.io.deq.bits(31, 0)
    bitsFilled := 32.U
  }
  when (io.dmaOutput.fire()) {
    addrReg := addrReg + io.dmaOutput.bits.totalBytes
    wideData := wideData >> (io.dmaOutput.bits.totalBytes * 8.U)
    bitsFilled := bitsFilled - (io.dmaOutput.bits.totalBytes * 8.U)
  }

  dataQueue.io.deq.ready := bitsFilled === 0.U
  dataQueue.io.enq <> io.dataIn
  io.dmaOutput.valid := bitsFilled > 0.U
  io.dmaOutput.bits.addr := addrReg
  io.dmaOutput.bits.data := wideData
  when (beatBytes.U <  4.U) {
    io.dmaOutput.bits.totalBytes := beatBytes.U
  } .otherwise {
    io.dmaOutput.bits.totalBytes := 4.U
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
