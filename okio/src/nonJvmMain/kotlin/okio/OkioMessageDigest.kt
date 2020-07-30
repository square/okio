package okio

import okio.internal.Sha1MessageDigest
import okio.internal.Sha256MessageDigest
import okio.internal.Sha512MessageDigest

internal actual fun newMessageDigest(
  algorithm: String
): OkioMessageDigest = when (algorithm) {
  "SHA-1" -> Sha1MessageDigest()
  "SHA-256" -> Sha256MessageDigest()
  "SHA-512" -> Sha512MessageDigest()
  // "MD5" -> MD5MessageDigest()
  else -> throw IllegalArgumentException("$algorithm is not a hashing algorithm")
}
