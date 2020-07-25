package okio.internal

import okio.OkioMessageDigest

internal class Sha256MessageDigest : OkioMessageDigest {
  override fun update(input: ByteArray) {
    TODO("Not yet implemented")
  }

  override fun digest(): ByteArray {
    TODO("Not yet implemented")
  }
}

private data class Sha256Digest(
  val h0: UInt,
  val h1: UInt,
  val h2: UInt,
  val h3: UInt,
  val h4: UInt,
  val h5: UInt,
  val h6: UInt,
  val h7: UInt
)