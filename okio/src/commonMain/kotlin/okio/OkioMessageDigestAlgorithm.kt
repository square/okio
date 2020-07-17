package okio

enum class OkioMessageDigestAlgorithm(val value: String) {
  SHA_1("SHA-1"),
  SHA_256("SHA-256"),
  SHA_512("SHA-512"),
  MD5("MD5")
}