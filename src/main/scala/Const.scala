package aes

import chisel3._
import chisel3.experimental.ChiselEnum

// AES ISA
object AESISA {
  val KEYSETUP128   = 0.U(7.W)
  val KEYSETUP256   = 1.U(7.W)
  val ADDRESSLOAD   = 2.U(7.W)
  val ENCRYPTBLOCKS = 3.U(7.W)
  val DECRYPTBLOCKS = 4.U(7.W)
  val QUERYSTATUS   = 5.U(7.W)
}

// AES address map
object AESAddr {
  val CTRL   = 8.U(8.W)
  val STATUS = 9.U(8.W)
  val CONFIG = 10.U(8.W)
  val KEY    = 16.U(8.W)
  val TEXT   = 32.U(8.W)
  val RESULT = 48.U(8.W)
}

// Main Controller States
object CtrlState extends ChiselEnum {
  val sIdle, sKeySetup, sKeyExp, sWaitData, sDataSetup, sWaitStart, sAESRun, sWaitResult, sDataWrite = Value
}

// Memory Controller States
object MemState extends ChiselEnum {
  val sIdle, sReadReq, sReadIntoAES, sWriteReq, sWriteIntoMem = Value
}
