package okio.internal

// TODO refactor to typealias after this is resolved https://youtrack.jetbrains.com/issue/KT-37316
internal interface OkioMessageDigest {

  /**
   * Update the digest using [input]
   */
  fun update(input: ByteArray)

  /**
   * Complete the hash calculaion and return the hash as a [ByteArray]
   */
  fun digest(): ByteArray

  companion object {
    fun getInstance(algorithm: OkioMessageDigestAlgorithm): OkioMessageDigest = when (algorithm) {
      OkioMessageDigestAlgorithm.SHA_1 -> Sha1MessageDigest()
      else -> TODO()
    }
  }
}

internal expect class Sha1MessageDigest constructor(): OkioMessageDigest
