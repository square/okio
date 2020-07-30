package okio.internal

private val k = uintArrayOf(
  0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u,
  0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
  0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u,
  0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
  0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu,
  0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
  0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u,
  0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
  0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u,
  0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
  0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u,
  0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
  0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u,
  0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
  0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u,
  0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u
)

internal class Sha256MessageDigest : AbstractMessageDigest() {

  override var currentDigest = HashDigest(
    0x6a09e667u,
    0xbb67ae85u,
    0x3c6ef372u,
    0xa54ff53au,
    0x510e527fu,
    0x9b05688cu,
    0x1f83d9abu,
    0x5be0cd19u
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
