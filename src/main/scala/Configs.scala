package aes

import chipsalliance.rocketchip.config.{Config, Parameters}
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.tile.{BuildRoCC, OpcodeSet}

class WithAESAccel extends Config((site, here, up) => {
  case BuildRoCC => up(BuildRoCC) ++ Seq(
    (p: Parameters) => {
      val aes = LazyModule.apply(new AESAccel(OpcodeSet.custom0)(p))
      aes
    }
  )
})
