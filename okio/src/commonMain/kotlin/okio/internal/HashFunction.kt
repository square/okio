package okio.internal

/**
 * A cryptographic hash function
 */
internal interface HashFunction {
  fun update(input: ByteArray, offset: Int, byteCount: Int)

  fun digest(): ByteArray
}

internal expect fun newHashFunction(algorithm: String): HashFunction
