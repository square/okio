package okio

// TODO refactor to typealias after this is resolved https://youtrack.jetbrains.com/issue/KT-37316
interface OkioMessageDigest {

  /**
   * Update the digest using [input]
   */
  fun update(input: ByteArray)

  /**
   * Complete the hash calculaion and return the hash as a [ByteArray]
   */
  fun digest(): ByteArray
}

expect fun newMessageDigest(algorithm: String): OkioMessageDigest
