package okio.internal

import okio.OkioMessageDigest

private val k = ulongArrayOf(
  "428a2f98d728ae22".toULong(16), "7137449123ef65cd".toULong(16), "b5c0fbcfec4d3b2f".toULong(16), "e9b5dba58189dbbc".toULong(16), "3956c25bf348b538".toULong(16),
  "59f111f1b605d019".toULong(16), "923f82a4af194f9b".toULong(16), "ab1c5ed5da6d8118".toULong(16), "d807aa98a3030242".toULong(16), "12835b0145706fbe".toULong(16),
  "243185be4ee4b28c".toULong(16), "550c7dc3d5ffb4e2".toULong(16), "72be5d74f27b896f".toULong(16), "80deb1fe3b1696b1".toULong(16), "9bdc06a725c71235".toULong(16),
  "c19bf174cf692694".toULong(16), "e49b69c19ef14ad2".toULong(16), "efbe4786384f25e3".toULong(16), "0fc19dc68b8cd5b5".toULong(16), "240ca1cc77ac9c65".toULong(16),
  "2de92c6f592b0275".toULong(16), "4a7484aa6ea6e483".toULong(16), "5cb0a9dcbd41fbd4".toULong(16), "76f988da831153b5".toULong(16), "983e5152ee66dfab".toULong(16),
  "a831c66d2db43210".toULong(16), "b00327c898fb213f".toULong(16), "bf597fc7beef0ee4".toULong(16), "c6e00bf33da88fc2".toULong(16), "d5a79147930aa725".toULong(16),
  "06ca6351e003826f".toULong(16), "142929670a0e6e70".toULong(16), "27b70a8546d22ffc".toULong(16), "2e1b21385c26c926".toULong(16), "4d2c6dfc5ac42aed".toULong(16),
  "53380d139d95b3df".toULong(16), "650a73548baf63de".toULong(16), "766a0abb3c77b2a8".toULong(16), "81c2c92e47edaee6".toULong(16), "92722c851482353b".toULong(16),
  "a2bfe8a14cf10364".toULong(16), "a81a664bbc423001".toULong(16), "c24b8b70d0f89791".toULong(16), "c76c51a30654be30".toULong(16), "d192e819d6ef5218".toULong(16),
  "d69906245565a910".toULong(16), "f40e35855771202a".toULong(16), "106aa07032bbd1b8".toULong(16), "19a4c116b8d2d0c8".toULong(16), "1e376c085141ab53".toULong(16),
  "2748774cdf8eeb99".toULong(16), "34b0bcb5e19b48a8".toULong(16), "391c0cb3c5c95a63".toULong(16), "4ed8aa4ae3418acb".toULong(16), "5b9cca4f7763e373".toULong(16),
  "682e6ff3d6b2b8a3".toULong(16), "748f82ee5defb2fc".toULong(16), "78a5636f43172f60".toULong(16), "84c87814a1f0ab72".toULong(16), "8cc702081a6439ec".toULong(16),
  "90befffa23631e28".toULong(16), "a4506cebde82bde9".toULong(16), "bef9a3f7b2c67915".toULong(16), "c67178f2e372532b".toULong(16), "ca273eceea26619c".toULong(16),
  "d186b8c721c0c207".toULong(16), "eada7dd6cde0eb1e".toULong(16), "f57d4f7fee6ed178".toULong(16), "06f067aa72176fba".toULong(16), "0a637dc5a2c898a6".toULong(16),
  "113f9804bef90dae".toULong(16), "1b710b35131c471b".toULong(16), "28db77f523047d84".toULong(16), "32caab7b40c72493".toULong(16), "3c9ebe0a15c9bebc".toULong(16),
  "431d67c49c100d4c".toULong(16), "4cc5d4becb3e42b6".toULong(16), "597f299cfc657e2a".toULong(16), "5fcb6fab3ad6faec".toULong(16), "6c44198c4a475817".toULong(16)
)

internal class Sha512MessageDigest : OkioMessageDigest {

  private var messageLength: Long = 0
  private var unprocessed = byteArrayOf()
  private var currentDigest = ULongHashDigest(
    "6a09e667f3bcc908".toULong(16), // convert from string because
    "bb67ae8584caa73b".toULong(16), // constant values are out of range
    "3c6ef372fe94f82b".toULong(16), // using Long.toULong()
    "a54ff53a5f1d36f1".toULong(16),
    "510e527fade682d1".toULong(16),
    "9b05688c2b3e6c1f".toULong(16),
    "1f83d9abfb41bd6b".toULong(16),
    "5be0cd19137e2179".toULong(16)
  )

  override fun update(input: ByteArray) {
    for (chunk in (unprocessed + input).chunked(128)) {
      when (chunk.size) {
        128 -> {
          currentDigest = processChunk(chunk, currentDigest)
          messageLength += 128
        }
        else -> unprocessed = chunk
      }
    }
  }

  override fun digest(): ByteArray {
    val finalMessageLength = messageLength + unprocessed.size

    val finalMessage = byteArrayOf(
      *unprocessed,
      0x80.toByte(),
      *ByteArray(((112 - (finalMessageLength + 1) % 128) % 128).toInt()),
      *0L.toByteArray(), // append 64 0 bits because SHA-512 requires message length to be a 128 bit int
      *(finalMessageLength * 8L).toByteArray()
    )

    finalMessage.chunked(128).forEach { chunk ->
      currentDigest = processChunk(chunk, currentDigest)
    }

    return currentDigest.toByteArray()
  }

  private fun processChunk(chunk: ByteArray, currentDigest: ULongHashDigest): ULongHashDigest {
    require(chunk.size == 128)

    val w = ULongArray(80)
    chunk.chunked(8).forEachIndexed { index, bytes ->
      w[index] = bytes.toULong()
    }

    for (i in 16 until 80) {
      val s0 = (w[i - 15] rightRotate 1) xor (w[i - 15] rightRotate 8) xor (w[i - 15] shr 7)
      val s1 = (w[i - 2] rightRotate 19) xor (w[i - 2] rightRotate 61) xor (w[i - 2] shr 6)
      w[i] = w[i - 16] + s0 + w[i - 7] + s1
    }

    var (a, b, c, d, e, f, g, h) = currentDigest
    for (i in 0 until 80) {
      val s0 = (a rightRotate 28) xor (a rightRotate 34) xor (a rightRotate 39)
      val s1 = (e rightRotate 14) xor (e rightRotate 18) xor (e rightRotate 41)

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

    return ULongHashDigest(
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

private class ULongHashDigest(vararg val hashValues: ULong) {

  fun toByteArray() = ByteArray(hashValues.size * 8) { index ->
    val byteIndex = index % 8
    val hashValuesIndex = index / 8

    hashValues[hashValuesIndex].getByte(byteIndex)
  }

  operator fun get(index: Int): ULong = hashValues[index]

  operator fun component1(): ULong = hashValues[0]
  operator fun component2(): ULong = hashValues[1]
  operator fun component3(): ULong = hashValues[2]
  operator fun component4(): ULong = hashValues[3]
  operator fun component5(): ULong = hashValues[4]
  operator fun component6(): ULong = hashValues[5]
  operator fun component7(): ULong = hashValues[6]
  operator fun component8(): ULong = hashValues[7]
}