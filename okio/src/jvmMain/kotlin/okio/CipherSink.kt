package okio

import java.io.IOException
import javax.crypto.Cipher

class CipherSink internal constructor(private val sink: BufferedSink, private val cipher: Cipher) : Sink {
  constructor(sink: Sink, cipher: Cipher) : this(sink.buffer(), cipher)

  private var closed = false

  @Throws(IOException::class)
  override fun write(source: Buffer, byteCount: Long) {
    checkOffsetAndCount(source.size, 0, byteCount)
    check(!closed) { "closed" }

    var remaining = byteCount
    while (remaining > 0) {
      val head = source.head!!
      var toCipher = minOf(remaining, head.limit - head.pos).toInt()
      var outputSize = cipher.getOutputSize(toCipher)
      // TODO: Consider using fixed block size (imposed and verified in constructor)
      while (outputSize > Segment.SIZE) {
        toCipher /= 2
        outputSize = cipher.getOutputSize(toCipher)
      }

      val buffer = sink.buffer
      val s = buffer.writableSegment(outputSize)
      val ciphered = cipher.update(head.data, head.pos, toCipher, s.data, s.limit)
      s.limit += ciphered
      buffer.size += ciphered

      if (s.pos == s.limit) {
        buffer.head = s.pop()
        SegmentPool.recycle(s)
      }

      source.size -= toCipher
      head.pos += toCipher
      if (head.pos == head.limit) {
        source.head = head.pop()
        SegmentPool.recycle(head)
      }

      remaining -= toCipher
    }
  }

  override fun flush() {}

  override fun timeout(): Timeout =
    sink.timeout()

  @Throws(IOException::class)
  override fun close() {
    if (closed) return

    var thrown: Throwable? = null

    val outputSize = cipher.getOutputSize(0)
    if (outputSize != 0) {
      val buffer = sink.buffer
      val s = buffer.writableSegment(outputSize)

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
    }

    try {
      sink.close()
    } catch (e: Throwable) {
      if (thrown == null) thrown = e
    }

    closed = true

    if (thrown != null) throw thrown
  }
}

fun Sink.cipherSink(cipher: Cipher): CipherSink =
  CipherSink(this, cipher)
