package okio.internal

private val k = uintArrayOf(
  0x428a2f98.toUInt(), 0x71374491.toUInt(), 0xb5c0fbcf.toUInt(), 0xe9b5dba5.toUInt(),
  0x3956c25b.toUInt(), 0x59f111f1.toUInt(), 0x923f82a4.toUInt(), 0xab1c5ed5.toUInt(),
  0xd807aa98.toUInt(), 0x12835b01.toUInt(), 0x243185be.toUInt(), 0x550c7dc3.toUInt(),
  0x72be5d74.toUInt(), 0x80deb1fe.toUInt(), 0x9bdc06a7.toUInt(), 0xc19bf174.toUInt(),
  0xe49b69c1.toUInt(), 0xefbe4786.toUInt(), 0x0fc19dc6.toUInt(), 0x240ca1cc.toUInt(),
  0x2de92c6f.toUInt(), 0x4a7484aa.toUInt(), 0x5cb0a9dc.toUInt(), 0x76f988da.toUInt(),
  0x983e5152.toUInt(), 0xa831c66d.toUInt(), 0xb00327c8.toUInt(), 0xbf597fc7.toUInt(),
  0xc6e00bf3.toUInt(), 0xd5a79147.toUInt(), 0x06ca6351.toUInt(), 0x14292967.toUInt(),
  0x27b70a85.toUInt(), 0x2e1b2138.toUInt(), 0x4d2c6dfc.toUInt(), 0x53380d13.toUInt(),
  0x650a7354.toUInt(), 0x766a0abb.toUInt(), 0x81c2c92e.toUInt(), 0x92722c85.toUInt(),
  0xa2bfe8a1.toUInt(), 0xa81a664b.toUInt(), 0xc24b8b70.toUInt(), 0xc76c51a3.toUInt(),
  0xd192e819.toUInt(), 0xd6990624.toUInt(), 0xf40e3585.toUInt(), 0x106aa070.toUInt(),
  0x19a4c116.toUInt(), 0x1e376c08.toUInt(), 0x2748774c.toUInt(), 0x34b0bcb5.toUInt(),
  0x391c0cb3.toUInt(), 0x4ed8aa4a.toUInt(), 0x5b9cca4f.toUInt(), 0x682e6ff3.toUInt(),
  0x748f82ee.toUInt(), 0x78a5636f.toUInt(), 0x84c87814.toUInt(), 0x8cc70208.toUInt(),
  0x90befffa.toUInt(), 0xa4506ceb.toUInt(), 0xbef9a3f7.toUInt(), 0xc67178f2.toUInt()
)

internal class Sha256MessageDigest : AbstractMessageDigest() {

  override var currentDigest = HashDigest(
    0x6a09e667.toUInt(),
    0xbb67ae85.toUInt(),
    0x3c6ef372.toUInt(),
    0xa54ff53a.toUInt(),
    0x510e527f.toUInt(),
    0x9b05688c.toUInt(),
    0x1f83d9ab.toUInt(),
    0x5be0cd19.toUInt()
  )

  override fun processChunk(chunk: ByteArray, currentDigest: HashDigest): HashDigest {
    require(chunk.size == 64)

    val w = UIntArray(64)
    chunk.chunked(4).forEachIndexed { index, bytes ->
      w[index] = bytes.toUInt()
    }

    for (i in 16 until 64) {
      val s0 = (w[i - 15] rightRotate 7) xor (w[i - 15] rightRotate 18) xor (w[i - 15] shr 3)
      val s1 = (w[i - 2] rightRotate 17) xor (w[i - 2] rightRotate 19) xor (w[i - 2] shr 10)
      w[i] = w[i - 16] + s0 + w[i - 7] + s1
    }

    var (a, b, c, d, e, f, g, h) = currentDigest
    for (i in 0 until 64) {
      val s0 = (a rightRotate 2) xor (a rightRotate 13) xor (a rightRotate 22)
      val s1 = (e rightRotate 6) xor (e rightRotate 11) xor (e rightRotate 25)

      val ch = (e and f) xor (e.inv() and g)
      val maj = (a and b) xor (a and c) xor (b and c)

      val t1 = h + s1 + ch + k[i] + w[i]
      val t2 = s0 + maj

      h = g
      g = f
      f = e
      e = d + t1
      d = c
      c = b
      b = a
      a = t1 + t2
    }

    return HashDigest(
      (currentDigest[0] + a),
      (currentDigest[1] + b),
      (currentDigest[2] + c),
      (currentDigest[3] + d),
      (currentDigest[4] + e),
      (currentDigest[5] + f),
      (currentDigest[6] + g),
      (currentDigest[7] + h)
    )
  }
}
