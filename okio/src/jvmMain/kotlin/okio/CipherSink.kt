package okio

import java.io.IOException
import javax.crypto.Cipher

class CipherSink internal constructor(private val sink: BufferedSink, private val cipher: Cipher) : Sink {
  constructor(sink: Sink, cipher: Cipher) : this(sink.buffer(), cipher)

  private val blockSize = cipher.blockSize
  private var closed = false

  init {
    // Require block cipher, and check for unsupported (too large) block size (should never happen with standard algorithms)
    require(blockSize > 0) { "Block cipher required $cipher" }
    require(blockSize <= Segment.SIZE) { "Cipher block size $blockSize too large $cipher" }
  }

  @Throws(IOException::class)
  override fun write(source: Buffer, byteCount: Long) {
    checkOffsetAndCount(source.size, 0, byteCount)
    check(!closed) { "closed" }

    var remaining = byteCount
    while (remaining > 0) {
      val size = update(source, remaining)
      remaining -= size
    }
  }

  private fun update(source: Buffer, remaining: Long): Int {
    val head = source.head!!
    val size = minOf(remaining, head.limit - head.pos).toInt()
    val buffer = sink.buffer
    val s = buffer.writableSegment(size) // For block cipher, output size cannot exceed input size in update
    val ciphered = cipher.update(head.data, head.pos, size, s.data, s.limit)

    s.limit += ciphered
    buffer.size += ciphered

    if (s.pos == s.limit) {
      buffer.head = s.pop()
      SegmentPool.recycle(s)
    }

    source.size -= size
    head.pos += size

    if (head.pos == head.limit) {
      source.head = head.pop()
      SegmentPool.recycle(head)
    }
    return size
  }

  override fun flush() {}

  override fun timeout(): Timeout =
    sink.timeout()

  @Throws(IOException::class)
  override fun close() {
    if (closed) return

    var thrown = doFinal()

    try {
      sink.close()
    } catch (e: Throwable) {
      if (thrown == null) thrown = e
    }

    closed = true

    if (thrown != null) throw thrown
  }

  private fun doFinal(): Throwable? {
    var thrown: Throwable? = null

    val buffer = sink.buffer
    val s = buffer.writableSegment(blockSize)

    try {
      val ciphered = cipher.doFinal(s.data, s.limit)

      s.limit += ciphered
      buffer.size += ciphered
    } catch (e: Throwable) {
      thrown = e
    }

    if (s.pos == s.limit) {
      buffer.head = s.pop()
      SegmentPool.recycle(s)
    }

    return thrown
  }
}

fun Sink.cipherSink(cipher: Cipher): CipherSink =
  CipherSink(this, cipher)
