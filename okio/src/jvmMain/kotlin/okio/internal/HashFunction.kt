package okio.internal

import java.security.MessageDigest

// TODO refactor to typealias after this is resolved https://youtrack.jetbrains.com/issue/KT-37316
internal actual fun newHashFunction(algorithm: String) = object : HashFunction {

  private val digest = MessageDigest.getInstance(algorithm)

  override fun update(input: ByteArray, offset: Int, byteCount: Int) = digest.update(
    input,
    offset,
    byteCount
  )

  override fun digest() = digest.digest()
}