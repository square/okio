package okio

internal actual fun newMessageDigest(
  algorithm: String
): OkioMessageDigest = when (algorithm) {
  "SHA-1" -> Sha1MessageDigest()
  "SHA-256" -> Sha256MessageDigest()
  "SHA-512" -> Sha512MessageDigest()
  "MD5" -> MD5MessageDigest()
  else -> throw IllegalArgumentException("$algorithm is not a hashing algorithm")
}

private class Sha1MessageDigest : OkioMessageDigest {
  override fun update(input: ByteArray) {
    TODO("Not yet implemented")
  }

  override fun digest(): ByteArray {
    TODO("Not yet implemented")
  }
}

private class Sha256MessageDigest : OkioMessageDigest {
  override fun update(input: ByteArray) {
    TODO("Not yet implemented")
  }

  override fun digest(): ByteArray {
    TODO("Not yet implemented")
  }
}

private class Sha512MessageDigest : OkioMessageDigest {
  override fun update(input: ByteArray) {
    TODO("Not yet implemented")
  }

  override fun digest(): ByteArray {
    TODO("Not yet implemented")
  }
}

private class MD5MessageDigest : OkioMessageDigest {
  override fun update(input: ByteArray) {
    TODO("Not yet implemented")
  }

  override fun digest(): ByteArray {
    TODO("Not yet implemented")
  }
}
