package okio.internal

internal class Sha1MessageDigest : AbstractMessageDigest() {

  override var currentDigest = HashDigest(
    0x67452301u,
    0xEFCDAB89u,
    0x98BADCFEu,
    0x10325476u,
    0xC3D2E1F0u
  )

  override fun processChunk(chunk: ByteArray, currentDigest: HashDigest): HashDigest {
    require(chunk.size == 64)

    val words = UIntArray(80)
    chunk.chunked(4).forEachIndexed { index, bytes ->
      words[index] = bytes.toBigEndianUInt()
    }

    for (i in 16 until 80) {
      words[i] = (words[i - 3] xor words[i - 8] xor words[i - 14] xor words[i - 16]) leftRotate 1
    }

    var (a, b, c, d, e) = currentDigest

    for (i in 0 until 80) {
      val (f, k) = when (i) {
        in 0..19 -> (d xor (b and (c xor d))) to 0x5A827999.toUInt()
        in 20..39 -> (b xor c xor d) to 0x6ED9EBA1.toUInt()
        in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toUInt()
        in 60..79 -> (b xor c xor d) to 0xCA62C1D6.toUInt()
        else -> error("Index is wonky, this should never happen")
      }

      val updatedDigest = HashDigest(
        ((a leftRotate 5) + f + e + k + words[i]) and UInt.MAX_VALUE,
        a,
        b leftRotate 30,
        c,
        d
      )

      a = updatedDigest[0]
      b = updatedDigest[1]
      c = updatedDigest[2]
      d = updatedDigest[3]
      e = updatedDigest[4]
    }

    return HashDigest(
      (currentDigest[0] + a),
      (currentDigest[1] + b),
      (currentDigest[2] + c),
      (currentDigest[3] + d),
      (currentDigest[4] + e)
    )
  }
}
