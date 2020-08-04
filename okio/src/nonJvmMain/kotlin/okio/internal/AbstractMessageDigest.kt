package okio.internal

import okio.OkioMessageDigest

internal abstract class AbstractMessageDigest : OkioMessageDigest {

  private var messageLength: Long = 0
  private var unprocessed: ByteArray = byteArrayOf()
  protected abstract var currentDigest: HashDigest

  override fun update(input: ByteArray) {
    for (chunk in (unprocessed + input).chunked(64)) {
      when (chunk.size) {
        64 -> {
          currentDigest = processChunk(chunk, currentDigest)
          messageLength += 64
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
      *ByteArray(((56 - (finalMessageLength + 1) % 64) % 64).toInt()),
      *(finalMessageLength * 8L).toBigEndianByteArray()
    )

    finalMessage.chunked(64).forEach { chunk ->
      currentDigest = processChunk(chunk, currentDigest)
    }

    return currentDigest.toBigEndianByteArray()
  }

  protected abstract fun processChunk(chunk: ByteArray, currentDigest: HashDigest): HashDigest
}