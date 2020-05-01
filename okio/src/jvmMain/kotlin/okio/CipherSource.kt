package okio

import java.io.IOException
import javax.crypto.Cipher

class CipherSource internal constructor(private val source: BufferedSource, private val cipher: Cipher) : Source {
  constructor(source: Source, cipher: Cipher) : this(source.buffer(), cipher)

  private val buffer = Buffer()
  private var final = false
  private var closed = false

  @Throws(IOException::class)
  override fun read(sink: Buffer, byteCount: Long): Long {
    require(byteCount >= 0) { "byteCount < 0: $byteCount" }
    check(!closed) { "closed" }
    if (byteCount == 0L) return 0
    if (final) return buffer.read(sink, byteCount)

    while (buffer.size == 0L) {
      if (source.exhausted()) {
        final = true
        return readFinal(sink, byteCount)
      }

      val head = source.buffer.head!!
      val size = head.limit - head.pos
      val toCipher = cipher.getOutputSize(size)
      val s = buffer.writableSegment(toCipher)
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

    return buffer.read(sink, byteCount)
  }

  private fun readFinal(sink: Buffer, byteCount: Long): Long {
    val toCipher = cipher.getOutputSize(0)
    val s = buffer.writableSegment(toCipher)
    val ciphered = cipher.doFinal(s.data, s.pos)
    s.limit += ciphered
    buffer.size += ciphered

    if (s.pos == s.limit) {
      buffer.head = s.pop()
      SegmentPool.recycle(s)
    }

    return buffer.read(sink, byteCount)
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
