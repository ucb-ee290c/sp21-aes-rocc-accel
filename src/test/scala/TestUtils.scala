package aes

import chisel3._
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.tile.RoCCCommand
import verif.TLMemoryModel._
import verif.VerifBundleUtils.{RoCCCommandHelper, RoCCInstructionHelper}

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import scala.util.Random

package object AESTestUtils {
  // Helper methods for creating a RoCCCommand
  def keyLoad128(addr: BigInt)(implicit p: Parameters): RoCCCommand = RoCCCommandHelper(inst = RoCCInstructionHelper(xs1 = true.B), rs1 = addr.U)
  def keyLoad256(addr: BigInt)(implicit p: Parameters): RoCCCommand = RoCCCommandHelper(inst = RoCCInstructionHelper(funct = 1.U, xs1 = true.B), rs1 = addr.U)
  def addrLoad(src: BigInt, dest: BigInt)(implicit p: Parameters): RoCCCommand = RoCCCommandHelper(inst = RoCCInstructionHelper(funct = 2.U, xs1 = true.B, xs2 = true.B),
    rs1 = src.U, rs2 = dest.U)
  def encBlock(count: Int)(implicit p: Parameters): RoCCCommand = RoCCCommandHelper(inst = RoCCInstructionHelper(funct = 3.U, xs1 = true.B), rs1 = count.U)
  def decBlock(count: Int)(implicit p: Parameters): RoCCCommand = RoCCCommandHelper(inst = RoCCInstructionHelper(funct = 4.U, xs1 = true.B), rs1 = count.U)
  def getStatus(rd: Int)(implicit p: Parameters): RoCCCommand = {
    assert(rd < 32, s"RD register must be less than 32. Given: $rd")
    RoCCCommandHelper(inst = RoCCInstructionHelper(funct = 5.U, xd = true.B, rd = rd.U))
  }
  def getInterrupt(rd: Int)(implicit p: Parameters): RoCCCommand = {
    assert(rd < 32, s"RD register must be less than 32. Given: $rd")
    RoCCCommandHelper(inst = RoCCInstructionHelper(funct = 6.U, xd = true.B, rd = rd.U))
  }

  // Generates random (but legal) addresses for key, source text, dest text
  // Legal key address: anything beatByte aligned
  // Legal data address: anything beatByte aligned and != key addr, and if src/dest overlap it must directly overlap (same address, destructive)
  // (keyAddr: BigInt, srcAddr: BigInt, destAddr: BigInt)
  def getRandomKeyTextAddr(textBlocks: Int, destructive: Boolean, beatBytes: Int, r: Random): (BigInt, BigInt, BigInt) = {
    assert(textBlocks > 0, s"# of text block must be at least 1. Given: $textBlocks")
    assert(beatBytes == 16, "TEMP: beatBytes must be 16 (see accelerator test for more details)")

    val keyAddr = BigInt(32, r) & ~(beatBytes - 1)
    var srcAddr = BigInt(32, r) & ~(beatBytes - 1)
    while (keyAddr <= srcAddr && srcAddr <= (keyAddr + 32)) {
      srcAddr = BigInt(32, r) & ~(beatBytes - 1)
    }
    if (destructive) {
      (keyAddr, srcAddr, srcAddr)
    } else {
      var destAddr = BigInt(32, r) & ~(beatBytes - 1)
      while ((keyAddr <= srcAddr && srcAddr <= (keyAddr + 32)) || (srcAddr <= destAddr && destAddr <= (srcAddr + textBlocks * 16))) {
        destAddr = BigInt(32, r) & ~(beatBytes - 1)
      }
      (keyAddr, srcAddr, destAddr)
    }
  }

  // Helper method for creating TLMemoryModel state with initial AES Data (key, text)
  // Assumes all addresses are legal (beatBytes aligned)
  // Assumes keyData is 256 bits, and textData is 128 bits per block
  def getTLMemModelState(keyAddr: BigInt, keyData: BigInt, textAddr: BigInt, textData: Seq[BigInt], beatBytes: Int): State = {
    // TODO Update function when beatBytes can be anything other than 16

    var initMem: Map[WordAddr, BigInt] = Map ()
    val keyWordAddr = keyAddr / beatBytes
    initMem = initMem + (keyWordAddr.toLong -> (keyData & BigInt("1" * 128, 2)))
    initMem = initMem + ((keyWordAddr + 1).toLong -> (keyData >> 128))

    val txtWordAddr = textAddr / beatBytes
    for (i <- textData.indices) {
      // Blocks are not in little-endian order, but doesn't matter as each block is independent
      initMem = initMem + ((txtWordAddr + i).toLong -> textData(i))
    }

    State.init(initMem, beatBytes)
  }

  // Generates random stimulus for AES Accelerator
  // (keyAddr: BigInt, keyData (256b post-padded): BigInt, srcAddr: BigInt, textData: Seq[BigInt], destAddr: BigInt, memState: TLMemModel.State)
  def genAESStim(keySize: Int, textBlocks: Int, destructive: Boolean, beatBytes: Int, r: Random): (BigInt, BigInt, BigInt, Seq[BigInt], BigInt, State) = {
    // Temporary assert
    assert(beatBytes == 16, "TEMP: beatBytes must be 16 (see accelerator test for more details)")

    // Generate Addresses
    val addresses = getRandomKeyTextAddr(textBlocks, destructive, beatBytes, r)
    val keyAddr = addresses._1
    val srcAddr = addresses._2
    val dstAddr = addresses._3

    // Generate key
    assert(keySize == 128 || keySize == 256, s"KeySize must be 128 OR 256. Given: $keySize")
    var keyData: BigInt = 0
    if (keySize == 128) {
      keyData = BigInt(128, r) << 128
    } else {
      keyData = BigInt(256, r)
    }

    // Generate Source Text
    val srcData = Seq.fill(textBlocks)(BigInt(128, r))

    // Generate State
    val state = getTLMemModelState(keyAddr, keyData, srcAddr, srcData, beatBytes)

    if (keySize == 128) {
      (keyAddr + 16, keyData, srcAddr, srcData, dstAddr, state)
    } else {
      (keyAddr, keyData, srcAddr, srcData, dstAddr, state)
    }
  }

  // Conditional if last block of result data has been written
  // InitData should be 0 unless destructive
  def finishedWriting(state: State, destAddr: BigInt, txtBlocks: Int, initData: BigInt, beatBytes: Int): Boolean = {
    // Assumes that beatBytes is at most 16 (or else need to mask/shift for correct data)
    read(state.mem, (destAddr/beatBytes + txtBlocks*(16/beatBytes) - 1).toLong, beatBytes, -1) != initData
  }

  // Checks if output matches standard AES library (ECB)
  def checkResult(keySize: Int, key: BigInt, srcData: Seq[BigInt], dstAddr: BigInt, encrypt: Boolean, state: State, beatBytes: Int): Boolean = {
    val cipher = AESECBCipher(keySize, key, encrypt)
    val results = srcData.map(x => BigInt(Array(0.toByte) ++ cipher.doFinal(x.toByteArray.reverse.padTo(16, 0.toByte).reverse.takeRight(16))))

    var matched = true
    for (i <- results.indices) {
      // TODO Change when beatBytes != 16
      val actual = read(state.mem, (dstAddr/beatBytes + i).toLong, beatBytes, -1)
      if (actual != results(i)) {
        println(s"Match failed for block $i. Expected: ${results(i).toString(16).toUpperCase()} " +
          s"Actual: ${actual.toString(16).toUpperCase()}")
        matched = false
      }
    }
    if (!matched) println("At least one encryption/decryption operation did not match the SW model. See above log.")
    matched
  }

  // Function that returns a cipher object (the SW model)
  def AESECBCipher(keySize: Int, key: BigInt, encrypt: Boolean): Cipher = {
    assert(keySize == 128 || keySize == 256, s"KeySize must be 128 OR 256. Given: $keySize")

    val cipher: Cipher = Cipher.getInstance("AES/ECB/NoPadding")
    val keyBArr = key.toByteArray.reverse.padTo(keySize/8, 0.toByte).reverse.takeRight(keySize/8)
    if (encrypt) {
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBArr, "AES"))
    } else {
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBArr, "AES"))
    }
    cipher
  }
}
