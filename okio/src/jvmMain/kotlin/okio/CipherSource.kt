package okio

import java.io.IOException
import javax.crypto.Cipher

class CipherSource internal constructor(private val source: BufferedSource, private val cipher: Cipher) : Source {
  constructor(source: Source, cipher: Cipher) : this(source.buffer(), cipher)

  private val blockSize = cipher.blockSize
  private val buffer = Buffer()
  private var final = false
  private var closed = false

  init {
    // Require block cipher, and check for unsupported (too large) block size (should never happen with standard algorithms)
    require(blockSize > 0) { "Block cipher required $cipher" }
    require(blockSize <= Segment.SIZE) { "Cipher block size $blockSize too large $cipher" }
  }

  @Throws(IOException::class)
  override fun read(sink: Buffer, byteCount: Long): Long {
    require(byteCount >= 0) { "byteCount < 0: $byteCount" }
    check(!closed) { "closed" }
    if (byteCount == 0L) return 0
    if (final) return buffer.read(sink, byteCount)

    while (buffer.size == 0L) {
      if (source.exhausted()) {
        final = true
        doFinal()
        break
      } else {
        update()
      }
    }

    return buffer.read(sink, byteCount)
  }

  private fun update() {
    val head = source.buffer.head!!
    val size = head.limit - head.pos
    val s = buffer.writableSegment(size) // For block cipher, output size cannot exceed input size in update
    val ciphered =
      cipher.update(head.data, head.pos, head.limit, s.data, s.pos)

    source.skip(size.toLong())

    s.limit += ciphered
    buffer.size += ciphered

    if (s.pos == s.limit) {
      buffer.head = head.pop()
      SegmentPool.recycle(s)
    }
  }

  private fun doFinal() {
    val s = buffer.writableSegment(blockSize)
    val ciphered = cipher.doFinal(s.data, s.pos)

    s.limit += ciphered
    buffer.size += ciphered

    if (s.pos == s.limit) {
      buffer.head = s.pop()
      SegmentPool.recycle(s)
    }
  }

  override fun timeout(): Timeout =
    source.timeout()

  @Throws(IOException::class)
  override fun close() {
    closed = true
    source.close()
  }
}

fun Source.cipherSource(cipher: Cipher): CipherSource =
  CipherSource(this, cipher)
