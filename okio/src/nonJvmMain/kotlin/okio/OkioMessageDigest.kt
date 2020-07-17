package okio

actual fun newMessageDigest(
  algorithm: OkioMessageDigestAlgorithm
): OkioMessageDigest = when (algorithm) {
  OkioMessageDigestAlgorithm.SHA_1 -> Sha1MessageDigest()
  OkioMessageDigestAlgorithm.SHA_256 -> Sha256MessageDigest()
  OkioMessageDigestAlgorithm.SHA_512 -> Sha512MessageDigest()
  OkioMessageDigestAlgorithm.MD5 -> MD5MessageDigest()
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
