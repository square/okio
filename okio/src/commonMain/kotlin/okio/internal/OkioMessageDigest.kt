package okio.internal

interface OkioMessageDigest {

  fun update(input: ByteArray)

  fun digest(): ByteArray
}

fun newMessageDigest(algorithm: String): OkioMessageDigest = when (algorithm) {
  "SHA-1" -> Sha1MessageDigest()
  "SHA-256" -> Sha256MessageDigest()
  "SHA-512" -> Sha512MessageDigest()
  "MD5" -> MD5MessageDigest()
  else -> error("Invalid algorithm $algorithm")
}