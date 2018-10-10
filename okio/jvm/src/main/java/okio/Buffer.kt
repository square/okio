/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio

import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel
import java.nio.charset.Charset
import java.security.InvalidKeyException
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A collection of bytes in memory.
 *
 * **Moving data from one buffer to another is fast.** Instead of copying bytes from one place in
 * memory to another, this class just changes ownership of the underlying byte arrays.
 *
 * **This buffer grows with your data.** Just like ArrayList, each buffer starts small. It consumes
 * only the memory it needs to.
 *
 * **This buffer pools its byte arrays.** When you allocate a byte array in Java, the runtime must
 * zero-fill the requested array before returning it to you. Even if you're going to write over that
 * space anyway. This class avoids zero-fill and GC churn by pooling byte arrays.
 */
class Buffer : BufferedSource, BufferedSink, Cloneable, ByteChannel {
  @JvmField internal var head: Segment? = null

  @get:JvmName("size")
  var size: Long = 0L
    internal set

  override fun buffer() = this

  override val buffer get() = this

  override fun outputStream(): OutputStream {
    return object : OutputStream() {
      override fun write(b: Int) {
        writeByte(b)
      }

      override fun write(data: ByteArray, offset: Int, byteCount: Int) {
        this@Buffer.write(data, offset, byteCount)
      }

      override fun flush() {}

      override fun close() {}

      override fun toString(): String = "${this@Buffer}.outputStream()"
    }
  }

  override fun emitCompleteSegments() = this // Nowhere to emit to!

  override fun emit() = this // Nowhere to emit to!

  override fun exhausted() = size == 0L

  @Throws(EOFException::class)
  override fun require(byteCount: Long) {
    if (size < byteCount) throw EOFException()
  }

  override fun request(byteCount: Long) = size >= byteCount

  override fun peek(): BufferedSource {
    return PeekSource(this).buffer()
  }

  override fun inputStream(): InputStream {
    return object : InputStream() {
      override fun read(): Int {
        return if (size > 0L) {
          readByte() and 0xff
        } else {
          -1
        }
      }

      override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int {
        return this@Buffer.read(sink, offset, byteCount)
      }

      override fun available() = minOf(size, Integer.MAX_VALUE).toInt()

      override fun close() {}

      override fun toString() = "${this@Buffer}.inputStream()"
    }
  }

  /** Copy `byteCount` bytes from this, starting at `offset`, to `out`. */
  @Throws(IOException::class)
  @JvmOverloads
  fun copyTo(
    out: OutputStream,
    offset: Long = 0L,
    byteCount: Long = size - offset
  ): Buffer {
    var offset = offset
    var byteCount = byteCount
    checkOffsetAndCount(size, offset, byteCount)
    if (byteCount == 0L) return this

    // Skip segments that we aren't copying from.
    var s = head
    while (offset >= s!!.limit - s.pos) {
      offset -= (s.limit - s.pos).toLong()
      s = s.next
    }

    // Copy from one segment at a time.
    while (byteCount > 0L) {
      val pos = (s!!.pos + offset).toInt()
      val toCopy = minOf(s.limit - pos, byteCount).toInt()
      out.write(s.data, pos, toCopy)
      byteCount -= toCopy.toLong()
      offset = 0L
      s = s.next
    }

    return this
  }

  /** Copy `byteCount` bytes from this, starting at `offset`, to `out`.  */
  fun copyTo(
    out: Buffer,
    offset: Long = 0L,
    byteCount: Long = size - offset
  ): Buffer {
    var offset = offset
    var byteCount = byteCount
    checkOffsetAndCount(size, offset, byteCount)
    if (byteCount == 0L) return this

    out.size += byteCount

    // Skip segments that we aren't copying from.
    var s = head
    while (offset >= s!!.limit - s.pos) {
      offset -= (s.limit - s.pos).toLong()
      s = s.next
    }

    // Copy one segment at a time.
    while (byteCount > 0L) {
      val copy = s!!.sharedCopy()
      copy.pos += offset.toInt()
      copy.limit = minOf(copy.pos + byteCount.toInt(), copy.limit)
      if (out.head == null) {
        copy.prev = copy
        copy.next = copy.prev
        out.head = copy.next
      } else {
        out.head!!.prev!!.push(copy)
      }
      byteCount -= (copy.limit - copy.pos).toLong()
      offset = 0L
      s = s.next
    }

    return this
  }

  /** Write `byteCount` bytes from this to `out`.  */
  @Throws(IOException::class)
  @JvmOverloads
  fun writeTo(out: OutputStream, byteCount: Long = size): Buffer {
    var byteCount = byteCount
    checkOffsetAndCount(size, 0, byteCount)

    var s = head
    while (byteCount > 0L) {
      val toCopy = minOf(byteCount, s!!.limit - s.pos).toInt()
      out.write(s.data, s.pos, toCopy)

      s.pos += toCopy
      size -= toCopy.toLong()
      byteCount -= toCopy.toLong()

      if (s.pos == s.limit) {
        val toRecycle = s
        s = toRecycle.pop()
        head = s
        SegmentPool.recycle(toRecycle)
      }
    }

    return this
  }

  /** Read and exhaust bytes from `input` into this.  */
  @Throws(IOException::class)
  fun readFrom(input: InputStream): Buffer {
    readFrom(input, Long.MAX_VALUE, true)
    return this
  }

  /** Read `byteCount` bytes from `input` into this.  */
  @Throws(IOException::class)
  fun readFrom(input: InputStream, byteCount: Long): Buffer {
    require(byteCount >= 0) { "byteCount < 0: $byteCount" }
    readFrom(input, byteCount, false)
    return this
  }

  @Throws(IOException::class)
  private fun readFrom(input: InputStream, byteCount: Long, forever: Boolean) {
    var byteCount = byteCount
    while (byteCount > 0L || forever) {
      val tail = writableSegment(1)
      val maxToCopy = minOf(byteCount, Segment.SIZE - tail.limit).toInt()
      val bytesRead = input.read(tail.data, tail.limit, maxToCopy)
      if (bytesRead == -1) {
        if (forever) return
        throw EOFException()
      }
      tail.limit += bytesRead
      size += bytesRead.toLong()
      byteCount -= bytesRead.toLong()
    }
  }

  /**
   * Returns the number of bytes in segments that are not writable. This is the number of bytes that
   * can be flushed immediately to an underlying sink without harming throughput.
   */
  fun completeSegmentByteCount(): Long {
    var result = size
    if (result == 0L) return 0L

    // Omit the tail if it's still writable.
    val tail = head!!.prev!!
    if (tail.limit < Segment.SIZE && tail.owner) {
      result -= (tail.limit - tail.pos).toLong()
    }

    return result
  }

  @Throws(EOFException::class)
  override fun readByte(): Byte {
    if (size == 0L) throw EOFException()

    val segment = head!!
    var pos = segment.pos
    val limit = segment.limit

    val data = segment.data
    val b = data[pos++]
    size -= 1L

    if (pos == limit) {
      head = segment.pop()
      SegmentPool.recycle(segment)
    } else {
      segment.pos = pos
    }

    return b
  }

  /** Returns the byte at `pos`.  */
  @JvmName("getByte")
  operator fun get(pos: Long): Byte {
    checkOffsetAndCount(size, pos, 1L)
    seek(pos) { s, offset ->
      return s!!.data[(s.pos + pos - offset).toInt()]
    }
  }

  @Throws(EOFException::class)
  override fun readShort(): Short {
    if (size < 2L) throw EOFException()

    val segment = head!!
    var pos = segment.pos
    val limit = segment.limit

    // If the short is split across multiple segments, delegate to readByte().
    if (limit - pos < 2) {
      val s = readByte() and 0xff shl 8 or (readByte() and 0xff)
      return s.toShort()
    }

    val data = segment.data
    val s = data[pos++] and 0xff shl 8 or (data[pos++] and 0xff)
    size -= 2L

    if (pos == limit) {
      head = segment.pop()
      SegmentPool.recycle(segment)
    } else {
      segment.pos = pos
    }

    return s.toShort()
  }

  @Throws(EOFException::class)
  override fun readInt(): Int {
    if (size < 4L) throw EOFException()

    val segment = head!!
    var pos = segment.pos
    val limit = segment.limit

    // If the int is split across multiple segments, delegate to readByte().
    if (limit - pos < 4L) {
      return (readByte() and 0xff shl 24
          or (readByte() and 0xff shl 16)
          or (readByte() and 0xff shl  8) // ktlint-disable no-multi-spaces
          or (readByte() and 0xff))
    }

    val data = segment.data
    val i = (data[pos++] and 0xff shl 24
        or (data[pos++] and 0xff shl 16)
        or (data[pos++] and 0xff shl 8)
        or (data[pos++] and 0xff))
    size -= 4L

    if (pos == limit) {
      head = segment.pop()
      SegmentPool.recycle(segment)
    } else {
      segment.pos = pos
    }

    return i
  }

  @Throws(EOFException::class)
  override fun readLong(): Long {
    if (size < 8L) throw EOFException()

    val segment = head!!
    var pos = segment.pos
    val limit = segment.limit

    // If the long is split across multiple segments, delegate to readInt().
    if (limit - pos < 8L) {
      return (readInt() and 0xffffffffL shl 32
          or (readInt() and 0xffffffffL))
    }

    val data = segment.data
    val v = (data[pos++] and 0xffL shl 56
        or (data[pos++] and 0xffL shl 48)
        or (data[pos++] and 0xffL shl 40)
        or (data[pos++] and 0xffL shl 32)
        or (data[pos++] and 0xffL shl 24)
        or (data[pos++] and 0xffL shl 16)
        or (data[pos++] and 0xffL shl  8) // ktlint-disable no-multi-spaces
        or (data[pos++] and 0xffL))
    size -= 8L

    if (pos == limit) {
      head = segment.pop()
      SegmentPool.recycle(segment)
    } else {
      segment.pos = pos
    }

    return v
  }

  @Throws(EOFException::class)
  override fun readShortLe() = readShort().reverseBytes()

  @Throws(EOFException::class)
  override fun readIntLe() = readInt().reverseBytes()

  @Throws(EOFException::class)
  override fun readLongLe() = readLong().reverseBytes()

  @Throws(EOFException::class)
  override fun readDecimalLong(): Long {
    if (size == 0L) throw EOFException()

    // This value is always built negatively in order to accommodate Long.MIN_VALUE.
    var value = 0L
    var seen = 0
    var negative = false
    var done = false

    val overflowZone = Long.MIN_VALUE / 10L
    var overflowDigit = Long.MIN_VALUE % 10L + 1

    do {
      val segment = head!!

      val data = segment.data
      var pos = segment.pos
      val limit = segment.limit

      while (pos < limit) {
        val b = data[pos]
        if (b >= '0'.toByte() && b <= '9'.toByte()) {
          val digit = '0'.toByte() - b

          // Detect when the digit would cause an overflow.
          if (value < overflowZone || value == overflowZone && digit < overflowDigit) {
            val buffer = Buffer().writeDecimalLong(value).writeByte(b.toInt())
            if (!negative) buffer.readByte() // Skip negative sign.
            throw NumberFormatException("Number too large: ${buffer.readUtf8()}")
          }
          value *= 10L
          value += digit.toLong()
        } else if (b == '-'.toByte() && seen == 0) {
          negative = true
          overflowDigit -= 1
        } else {
          if (seen == 0) {
            throw NumberFormatException(
                "Expected leading [0-9] or '-' character but was 0x${Integer.toHexString(
                    b.toInt())}")
          }
          // Set a flag to stop iteration. We still need to run through segment updating below.
          done = true
          break
        }
        pos++
        seen++
      }

      if (pos == limit) {
        head = segment.pop()
        SegmentPool.recycle(segment)
      } else {
        segment.pos = pos
      }
    } while (!done && head != null)

    size -= seen.toLong()
    return if (negative) value else -value
  }

  @Throws(EOFException::class)
  override fun readHexadecimalUnsignedLong(): Long {
    if (size == 0L) throw EOFException()

    var value = 0L
    var seen = 0
    var done = false

    do {
      val segment = head!!

      val data = segment.data
      var pos = segment.pos
      val limit = segment.limit

      while (pos < limit) {
        val digit: Int

        val b = data[pos]
        if (b >= '0'.toByte() && b <= '9'.toByte()) {
          digit = b - '0'.toByte()
        } else if (b >= 'a'.toByte() && b <= 'f'.toByte()) {
          digit = b - 'a'.toByte() + 10
        } else if (b >= 'A'.toByte() && b <= 'F'.toByte()) {
          digit = b - 'A'.toByte() + 10 // We never write uppercase, but we support reading it.
        } else {
          if (seen == 0) {
            throw NumberFormatException(
                "Expected leading [0-9a-fA-F] character but was 0x${Integer.toHexString(
                    b.toInt())}")
          }
          // Set a flag to stop iteration. We still need to run through segment updating below.
          done = true
          break
        }

        // Detect when the shift will overflow.
        if (value and -0x1000000000000000L != 0L) {
          val buffer = Buffer().writeHexadecimalUnsignedLong(value).writeByte(b.toInt())
          throw NumberFormatException("Number too large: " + buffer.readUtf8())
        }

        value = value shl 4
        value = value or digit.toLong()
        pos++
        seen++
      }

      if (pos == limit) {
        head = segment.pop()
        SegmentPool.recycle(segment)
      } else {
        segment.pos = pos
      }
    } while (!done && head != null)

    size -= seen.toLong()
    return value
  }

  override fun readByteString() = ByteString(readByteArray())

  @Throws(EOFException::class)
  override fun readByteString(byteCount: Long) = ByteString(readByteArray(byteCount))

  override fun select(options: Options): Int {
    val index = selectPrefix(options)
    if (index == -1) return -1

    // If the prefix match actually matched a full byte string, consume it and return it.
    val selectedSize = options.byteStrings[index].size
    skip(selectedSize.toLong())
    return index
  }

  /**
   * Returns the index of a value in options that is a prefix of this buffer. Returns -1 if no value
   * is found. This method does two simultaneous iterations: it iterates the trie and it iterates
   * this buffer. It returns when it reaches a result in the trie, when it mismatches in the trie,
   * and when the buffer is exhausted.
   *
   * @param selectTruncated true to return -2 if a possible result is present but truncated. For
   *     example, this will return -2 if the buffer contains [ab] and the options are [abc, abd].
   *     Note that this is made complicated by the fact that options are listed in preference order,
   *     and one option may be a prefix of another. For example, this returns -2 if the buffer
   *     contains [ab] and the options are [abc, a].
   */
  internal fun selectPrefix(options: Options, selectTruncated: Boolean = false): Int {
    val head = head ?: return if (selectTruncated) -2 else -1

    var s: Segment? = head
    var data = head.data
    var pos = head.pos
    var limit = head.limit

    val trie = options.trie
    var triePos = 0

    var prefixIndex = -1

    navigateTrie@
    while (true) {
      val scanOrSelect = trie[triePos++]

      val possiblePrefixIndex = trie[triePos++]
      if (possiblePrefixIndex != -1) {
        prefixIndex = possiblePrefixIndex
      }

      val nextStep: Int

      if (s == null) {
        break@navigateTrie
      } else if (scanOrSelect < 0) {
        // Scan: take multiple bytes from the buffer and the trie, looking for any mismatch.
        val scanByteCount = -1 * scanOrSelect
        val trieLimit = triePos + scanByteCount
        while (true) {
          val byte = data[pos++] and 0xff
          if (byte != trie[triePos++]) return prefixIndex // Fail 'cause we found a mismatch.
          val scanComplete = (triePos == trieLimit)

          // Advance to the next buffer segment if this one is exhausted.
          if (pos == limit) {
            s = s!!.next!!
            pos = s.pos
            data = s.data
            limit = s.limit
            if (s === head) {
              if (!scanComplete) break@navigateTrie // We were exhausted before the scan completed.
              s = null // We were exhausted at the end of the scan.
            }
          }

          if (scanComplete) {
            nextStep = trie[triePos]
            break
          }
        }
      } else {
        // Select: take one byte from the buffer and find a match in the trie.
        val selectChoiceCount = scanOrSelect
        val byte = data[pos++] and 0xff
        val selectLimit = triePos + selectChoiceCount
        while (true) {
          if (triePos == selectLimit) return prefixIndex // Fail 'cause we didn't find a match.

          if (byte == trie[triePos]) {
            nextStep = trie[triePos + selectChoiceCount]
            break
          }

          triePos++
        }

        // Advance to the next buffer segment if this one is exhausted.
        if (pos == limit) {
          s = s.next!!
          pos = s.pos
          data = s.data
          limit = s.limit
          if (s === head) {
            s = null // No more segments! The next trie node will be our last.
          }
        }
      }

      if (nextStep >= 0) return nextStep // Found a matching option.
      triePos = -nextStep // Found another node to continue the search.
    }

    // We break out of the loop above when we've exhausted the buffer without exhausting the trie.
    if (selectTruncated) return -2 // The buffer is a prefix of at least one option.
    return prefixIndex // Return any matches we encountered while searching for a deeper match.
  }

  @Throws(EOFException::class)
  override fun readFully(sink: Buffer, byteCount: Long) {
    if (size < byteCount) {
      sink.write(this, size) // Exhaust ourselves.
      throw EOFException()
    }
    sink.write(this, byteCount)
  }

  @Throws(IOException::class)
  override fun readAll(sink: Sink): Long {
    val byteCount = size
    if (byteCount > 0L) {
      sink.write(this, byteCount)
    }
    return byteCount
  }

  override fun readUtf8() = readString(size, Charsets.UTF_8)

  @Throws(EOFException::class)
  override fun readUtf8(byteCount: Long) = readString(byteCount, Charsets.UTF_8)

  override fun readString(charset: Charset) = readString(size, charset)

  @Throws(EOFException::class)
  override fun readString(byteCount: Long, charset: Charset): String {
    require(byteCount >= 0 && byteCount <= Integer.MAX_VALUE) { "byteCount: $byteCount" }
    if (size < byteCount) throw EOFException()
    if (byteCount == 0L) return ""

    val s = head!!
    if (s.pos + byteCount > s.limit) {
      // If the string spans multiple segments, delegate to readBytes().
      return String(readByteArray(byteCount), charset)
    }

    val result = String(s.data, s.pos, byteCount.toInt(), charset)
    s.pos += byteCount.toInt()
    size -= byteCount

    if (s.pos == s.limit) {
      head = s.pop()
      SegmentPool.recycle(s)
    }

    return result
  }

  @Throws(EOFException::class)
  override fun readUtf8Line(): String? {
    val newline = indexOf('\n'.toByte())

    return when {
      newline != -1L -> readUtf8Line(newline)
      size != 0L -> readUtf8(size)
      else -> null
    }
  }

  @Throws(EOFException::class)
  override fun readUtf8LineStrict() = readUtf8LineStrict(Long.MAX_VALUE)

  @Throws(EOFException::class)
  override fun readUtf8LineStrict(limit: Long): String {
    require(limit >= 0L) { "limit < 0: $limit" }
    val scanLength = if (limit == Long.MAX_VALUE) Long.MAX_VALUE else limit + 1L
    val newline = indexOf('\n'.toByte(), 0L, scanLength)
    if (newline != -1L) return readUtf8Line(newline)
    if (scanLength < size &&
        this[scanLength - 1] == '\r'.toByte() &&
        this[scanLength] == '\n'.toByte()) {
      return readUtf8Line(scanLength) // The line was 'limit' UTF-8 bytes followed by \r\n.
    }
    val data = Buffer()
    copyTo(data, 0, minOf(32, size))
    throw EOFException("\\n not found: limit=${minOf(size,
        limit)} content=${data.readByteString().hex()}${'…'}")
  }

  @Throws(EOFException::class)
  internal fun readUtf8Line(newline: Long): String {
    return when {
      newline > 0 && this[newline - 1] == '\r'.toByte() -> {
        // Read everything until '\r\n', then skip the '\r\n'.
        val result = readUtf8(newline - 1L)
        skip(2L)
        result
      }
      else -> {
        // Read everything until '\n', then skip the '\n'.
        val result = readUtf8(newline)
        skip(1L)
        result
      }
    }
  }

  @Throws(EOFException::class)
  override fun readUtf8CodePoint(): Int {
    if (size == 0L) throw EOFException()

    val b0 = this[0]
    var codePoint: Int
    val byteCount: Int
    val min: Int

    when {
      b0 and 0x80 == 0 -> {
        // 0xxxxxxx.
        codePoint = b0 and 0x7f
        byteCount = 1 // 7 bits (ASCII).
        min = 0x0
      }
      b0 and 0xe0 == 0xc0 -> {
        // 0x110xxxxx
        codePoint = b0 and 0x1f
        byteCount = 2 // 11 bits (5 + 6).
        min = 0x80
      }
      b0 and 0xf0 == 0xe0 -> {
        // 0x1110xxxx
        codePoint = b0 and 0x0f
        byteCount = 3 // 16 bits (4 + 6 + 6).
        min = 0x800
      }
      b0 and 0xf8 == 0xf0 -> {
        // 0x11110xxx
        codePoint = b0 and 0x07
        byteCount = 4 // 21 bits (3 + 6 + 6 + 6).
        min = 0x10000
      }
      else -> {
        // We expected the first byte of a code point but got something else.
        skip(1)
        return REPLACEMENT_CODE_POINT
      }
    }

    if (size < byteCount) {
      throw EOFException("size < " + byteCount + ": " + size +
        " (to read code point prefixed 0x" + Integer.toHexString(b0.toInt()) + ")")
    }

    // Read the continuation bytes. If we encounter a non-continuation byte, the sequence consumed
    // thus far is truncated and is decoded as the replacement character. That non-continuation byte
    // is left in the stream for processing by the next call to readUtf8CodePoint().
    for (i in 1 until byteCount) {
      val b = this[i.toLong()]
      if (b and 0xc0 == 0x80) {
        // 0x10xxxxxx
        codePoint = codePoint shl 6
        codePoint = codePoint or (b and 0x3f)
      } else {
        skip(i.toLong())
        return REPLACEMENT_CODE_POINT
      }
    }

    skip(byteCount.toLong())

    return when {
      codePoint > 0x10ffff -> {
        REPLACEMENT_CODE_POINT // Reject code points larger than the Unicode maximum.
      }
      codePoint in 0xd800..0xdfff -> {
        REPLACEMENT_CODE_POINT // Reject partial surrogates.
      }
      codePoint < min -> {
        REPLACEMENT_CODE_POINT // Reject overlong code points.
      }
      else -> codePoint
    }
  }

  override fun readByteArray() = readByteArray(size)

  @Throws(EOFException::class)
  override fun readByteArray(byteCount: Long): ByteArray {
    require(byteCount >= 0 && byteCount <= Integer.MAX_VALUE) { "byteCount: $byteCount" }
    if (size < byteCount) throw EOFException()

    val result = ByteArray(byteCount.toInt())
    readFully(result)
    return result
  }

  override fun read(sink: ByteArray) = read(sink, 0, sink.size)

  @Throws(EOFException::class)
  override fun readFully(sink: ByteArray) {
    var offset = 0
    while (offset < sink.size) {
      val read = read(sink, offset, sink.size - offset)
      if (read == -1) throw EOFException()
      offset += read
    }
  }

  override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int {
    checkOffsetAndCount(sink.size.toLong(), offset.toLong(), byteCount.toLong())

    val s = head ?: return -1
    val toCopy = minOf(byteCount, s.limit - s.pos)
    System.arraycopy(s.data, s.pos, sink, offset, toCopy)

    s.pos += toCopy
    size -= toCopy.toLong()

    if (s.pos == s.limit) {
      head = s.pop()
      SegmentPool.recycle(s)
    }

    return toCopy
  }

  @Throws(IOException::class)
  override fun read(sink: ByteBuffer): Int {
    val s = head ?: return -1

    val toCopy = minOf(sink.remaining(), s.limit - s.pos)
    sink.put(s.data, s.pos, toCopy)

    s.pos += toCopy
    size -= toCopy.toLong()

    if (s.pos == s.limit) {
      head = s.pop()
      SegmentPool.recycle(s)
    }

    return toCopy
  }

  /**
   * Discards all bytes in this buffer. Calling this method when you're done with a buffer will
   * return its segments to the pool.
   */
  fun clear() = skip(size)

  /** Discards `byteCount` bytes from the head of this buffer.  */
  @Throws(EOFException::class)
  override fun skip(byteCount: Long) {
    var byteCount = byteCount
    while (byteCount > 0) {
      val head = this.head ?: throw EOFException()

      val toSkip = minOf(byteCount, head.limit - head.pos).toInt()
      size -= toSkip.toLong()
      byteCount -= toSkip.toLong()
      head.pos += toSkip

      if (head.pos == head.limit) {
        this.head = head.pop()
        SegmentPool.recycle(head)
      }
    }
  }

  override fun write(byteString: ByteString): Buffer {
    byteString.write(this)
    return this
  }

  override fun writeUtf8(string: String) = writeUtf8(string, 0, string.length)

  override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): Buffer {
    require(beginIndex >= 0) { "beginIndex < 0: $beginIndex" }
    require(endIndex >= beginIndex) { "endIndex < beginIndex: $endIndex < $beginIndex" }
    require(endIndex <= string.length) { "endIndex > string.length: $endIndex > ${string.length}" }

    // Transcode a UTF-16 Java String to UTF-8 bytes.
    var i = beginIndex
    while (i < endIndex) {
      var c = string[i].toInt()

      when {
        c < 0x80 -> {
          val tail = writableSegment(1)
          val data = tail.data
          val segmentOffset = tail.limit - i
          val runLimit = minOf(endIndex, Segment.SIZE - segmentOffset)

          // Emit a 7-bit character with 1 byte.
          data[segmentOffset + i++] = c.toByte() // 0xxxxxxx

          // Fast-path contiguous runs of ASCII characters. This is ugly, but yields a ~4x performance
          // improvement over independent calls to writeByte().
          while (i < runLimit) {
            c = string[i].toInt()
            if (c >= 0x80) break
            data[segmentOffset + i++] = c.toByte() // 0xxxxxxx
          }

          val runSize = i + segmentOffset - tail.limit // Equivalent to i - (previous i).
          tail.limit += runSize
          size += runSize.toLong()
        }

        c < 0x800 -> {
          // Emit a 11-bit character with 2 bytes.
          val tail = writableSegment(2)
          /* ktlint-disable no-multi-spaces */
          tail.data[tail.limit    ] = (c shr 6          or 0xc0).toByte() // 110xxxxx
          tail.data[tail.limit + 1] = (c       and 0x3f or 0x80).toByte() // 10xxxxxx
          /* ktlint-enable no-multi-spaces */
          tail.limit += 2
          size += 2L
          i++
        }

        c < 0xd800 || c > 0xdfff -> {
          // Emit a 16-bit character with 3 bytes.
          val tail = writableSegment(3)
          /* ktlint-disable no-multi-spaces */
          tail.data[tail.limit    ] = (c shr 12          or 0xe0).toByte() // 1110xxxx
          tail.data[tail.limit + 1] = (c shr  6 and 0x3f or 0x80).toByte() // 10xxxxxx
          tail.data[tail.limit + 2] = (c        and 0x3f or 0x80).toByte() // 10xxxxxx
          /* ktlint-enable no-multi-spaces */
          tail.limit += 3
          size += 3L
          i++
        }

        else -> {
          // c is a surrogate. Make sure it is a high surrogate & that its successor is a low
          // surrogate. If not, the UTF-16 is invalid, in which case we emit a replacement
          // character.
          val low = (if (i + 1 < endIndex) string[i + 1].toInt() else 0)
          if (c > 0xdbff || low !in 0xdc00..0xdfff) {
            writeByte('?'.toInt())
            i++
          } else {
            // UTF-16 high surrogate: 110110xxxxxxxxxx (10 bits)
            // UTF-16 low surrogate:  110111yyyyyyyyyy (10 bits)
            // Unicode code point:    00010000000000000000 + xxxxxxxxxxyyyyyyyyyy (21 bits)
            val codePoint = 0x010000 + (c and 0x03ff shl 10 or (low and 0x03ff))

            // Emit a 21-bit character with 4 bytes.
            val tail = writableSegment(4)
            /* ktlint-disable no-multi-spaces */
            tail.data[tail.limit    ] = (codePoint shr 18          or 0xf0).toByte() // 11110xxx
            tail.data[tail.limit + 1] = (codePoint shr 12 and 0x3f or 0x80).toByte() // 10xxxxxx
            tail.data[tail.limit + 2] = (codePoint shr  6 and 0x3f or 0x80).toByte() // 10xxyyyy
            tail.data[tail.limit + 3] = (codePoint        and 0x3f or 0x80).toByte() // 10yyyyyy
            /* ktlint-enable no-multi-spaces */
            tail.limit += 4
            size += 4L
            i += 2
          }
        }
      }
    }

    return this
  }

  override fun writeUtf8CodePoint(codePoint: Int): Buffer {
    when {
      codePoint < 0x80 -> {
        // Emit a 7-bit code point with 1 byte.
        writeByte(codePoint)
      }
      codePoint < 0x800 -> {
        // Emit a 11-bit code point with 2 bytes.
        val tail = writableSegment(2)
        /* ktlint-disable no-multi-spaces */
        tail.data[tail.limit    ] = (codePoint shr 6          or 0xc0).toByte() // 110xxxxx
        tail.data[tail.limit + 1] = (codePoint       and 0x3f or 0x80).toByte() // 10xxxxxx
        /* ktlint-enable no-multi-spaces */
        tail.limit += 2
        size += 2L
      }
      codePoint in 0xd800..0xdfff -> {
        // Emit a replacement character for a partial surrogate.
        writeByte('?'.toInt())
      }
      codePoint < 0x10000 -> {
        // Emit a 16-bit code point with 3 bytes.
        val tail = writableSegment(3)
        /* ktlint-disable no-multi-spaces */
        tail.data[tail.limit    ] = (codePoint shr 12          or 0xe0).toByte() // 1110xxxx
        tail.data[tail.limit + 1] = (codePoint shr  6 and 0x3f or 0x80).toByte() // 10xxxxxx
        tail.data[tail.limit + 2] = (codePoint        and 0x3f or 0x80).toByte() // 10xxxxxx
        /* ktlint-enable no-multi-spaces */
        tail.limit += 3
        size += 3L
      }
      codePoint <= 0x10ffff -> {
        // Emit a 21-bit code point with 4 bytes.
        val tail = writableSegment(4)
        /* ktlint-disable no-multi-spaces */
        tail.data[tail.limit    ] = (codePoint shr 18          or 0xf0).toByte() // 11110xxx
        tail.data[tail.limit + 1] = (codePoint shr 12 and 0x3f or 0x80).toByte() // 10xxxxxx
        tail.data[tail.limit + 2] = (codePoint shr  6 and 0x3f or 0x80).toByte() // 10xxyyyy
        tail.data[tail.limit + 3] = (codePoint        and 0x3f or 0x80).toByte() // 10yyyyyy
        /* ktlint-enable no-multi-spaces */
        tail.limit += 4
        size += 4L
      }
      else -> {
        throw IllegalArgumentException("Unexpected code point: ${Integer.toHexString(codePoint)}")
      }
    }

    return this
  }

  override fun writeString(string: String, charset: Charset) = writeString(string, 0, string.length,
      charset)

  override fun writeString(
    string: String,
    beginIndex: Int,
    endIndex: Int,
    charset: Charset
  ): Buffer {
    require(beginIndex >= 0) { "beginIndex < 0: $beginIndex" }
    require(endIndex >= beginIndex) { "endIndex < beginIndex: $endIndex < $beginIndex" }
    require(endIndex <= string.length) { "endIndex > string.length: $endIndex > ${string.length}" }
    if (charset == Charsets.UTF_8) return writeUtf8(string, beginIndex, endIndex)
    val data = string.substring(beginIndex, endIndex).toByteArray(charset)
    return write(data, 0, data.size)
  }

  override fun write(source: ByteArray) = write(source, 0, source.size)

  override fun write(source: ByteArray, offset: Int, byteCount: Int): Buffer {
    var offset = offset
    checkOffsetAndCount(source.size.toLong(), offset.toLong(), byteCount.toLong())

    val limit = offset + byteCount
    while (offset < limit) {
      val tail = writableSegment(1)

      val toCopy = minOf(limit - offset, Segment.SIZE - tail.limit)
      System.arraycopy(source, offset, tail.data, tail.limit, toCopy)

      offset += toCopy
      tail.limit += toCopy
    }

    size += byteCount.toLong()
    return this
  }

  @Throws(IOException::class)
  override fun write(source: ByteBuffer): Int {
    val byteCount = source.remaining()
    var remaining = byteCount
    while (remaining > 0) {
      val tail = writableSegment(1)

      val toCopy = minOf(remaining, Segment.SIZE - tail.limit)
      source.get(tail.data, tail.limit, toCopy)

      remaining -= toCopy
      tail.limit += toCopy
    }

    size += byteCount.toLong()
    return byteCount
  }

  @Throws(IOException::class)
  override fun writeAll(source: Source): Long {
    var totalBytesRead = 0L
    while (true) {
      val readCount = source.read(this, Segment.SIZE.toLong())
      if (readCount == -1L) break
      totalBytesRead += readCount
    }
    return totalBytesRead
  }

  @Throws(IOException::class)
  override fun write(source: Source, byteCount: Long): BufferedSink {
    var byteCount = byteCount
    while (byteCount > 0L) {
      val read = source.read(this, byteCount)
      if (read == -1L) throw EOFException()
      byteCount -= read
    }
    return this
  }

  override fun writeByte(b: Int): Buffer {
    val tail = writableSegment(1)
    tail.data[tail.limit++] = b.toByte()
    size += 1L
    return this
  }

  override fun writeShort(s: Int): Buffer {
    val tail = writableSegment(2)
    val data = tail.data
    var limit = tail.limit
    data[limit++] = (s ushr 8 and 0xff).toByte()
    data[limit++] = (s        and 0xff).toByte() // ktlint-disable no-multi-spaces
    tail.limit = limit
    size += 2L
    return this
  }

  override fun writeShortLe(s: Int) = writeShort(s.toShort().reverseBytes().toInt())

  override fun writeInt(i: Int): Buffer {
    val tail = writableSegment(4)
    val data = tail.data
    var limit = tail.limit
    data[limit++] = (i ushr 24 and 0xff).toByte()
    data[limit++] = (i ushr 16 and 0xff).toByte()
    data[limit++] = (i ushr  8 and 0xff).toByte() // ktlint-disable no-multi-spaces
    data[limit++] = (i         and 0xff).toByte() // ktlint-disable no-multi-spaces
    tail.limit = limit
    size += 4L
    return this
  }

  override fun writeIntLe(i: Int) = writeInt(i.reverseBytes())

  override fun writeLong(v: Long): Buffer {
    val tail = writableSegment(8)
    val data = tail.data
    var limit = tail.limit
    data[limit++] = (v ushr 56 and 0xffL).toByte()
    data[limit++] = (v ushr 48 and 0xffL).toByte()
    data[limit++] = (v ushr 40 and 0xffL).toByte()
    data[limit++] = (v ushr 32 and 0xffL).toByte()
    data[limit++] = (v ushr 24 and 0xffL).toByte()
    data[limit++] = (v ushr 16 and 0xffL).toByte()
    data[limit++] = (v ushr  8 and 0xffL).toByte() // ktlint-disable no-multi-spaces
    data[limit++] = (v         and 0xffL).toByte() // ktlint-disable no-multi-spaces
    tail.limit = limit
    size += 8L
    return this
  }

  override fun writeLongLe(v: Long) = writeLong(v.reverseBytes())

  override fun writeDecimalLong(v: Long): Buffer {
    var v = v
    if (v == 0L) {
      // Both a shortcut and required since the following code can't handle zero.
      return writeByte('0'.toInt())
    }

    var negative = false
    if (v < 0L) {
      v = -v
      if (v < 0L) { // Only true for Long.MIN_VALUE.
        return writeUtf8("-9223372036854775808")
      }
      negative = true
    }

    // Binary search for character width which favors matching lower numbers.
    var width =
        if (v < 100000000L)
          if (v < 10000L)
            if (v < 100L)
              if (v < 10L) 1
              else 2
            else if (v < 1000L) 3
            else 4
          else if (v < 1000000L)
            if (v < 100000L) 5
            else 6
          else if (v < 10000000L) 7
          else 8
        else if (v < 1000000000000L)
          if (v < 10000000000L)
            if (v < 1000000000L) 9
            else 10
          else if (v < 100000000000L) 11
          else 12
        else if (v < 1000000000000000L)
          if (v < 10000000000000L) 13
          else if (v < 100000000000000L) 14
          else 15
        else if (v < 100000000000000000L)
          if (v < 10000000000000000L) 16
          else 17
        else if (v < 1000000000000000000L) 18
        else 19
    if (negative) {
      ++width
    }

    val tail = writableSegment(width)
    val data = tail.data
    var pos = tail.limit + width // We write backwards from right to left.
    while (v != 0L) {
      val digit = (v % 10).toInt()
      data[--pos] = DIGITS[digit]
      v /= 10
    }
    if (negative) {
      data[--pos] = '-'.toByte()
    }

    tail.limit += width
    this.size += width.toLong()
    return this
  }

  override fun writeHexadecimalUnsignedLong(v: Long): Buffer {
    var v = v
    if (v == 0L) {
      // Both a shortcut and required since the following code can't handle zero.
      return writeByte('0'.toInt())
    }

    val width = java.lang.Long.numberOfTrailingZeros(java.lang.Long.highestOneBit(v)) / 4 + 1

    val tail = writableSegment(width)
    val data = tail.data
    var pos = tail.limit + width - 1
    val start = tail.limit
    while (pos >= start) {
      data[pos] = DIGITS[(v and 0xF).toInt()]
      v = v ushr 4
      pos--
    }
    tail.limit += width
    size += width.toLong()
    return this
  }

  /**
   * Returns a tail segment that we can write at least `minimumCapacity`
   * bytes to, creating it if necessary.
   */
  internal fun writableSegment(minimumCapacity: Int): Segment {
    require(minimumCapacity >= 1 && minimumCapacity <= Segment.SIZE) { "unexpected capacity" }

    if (head == null) {
      val result = SegmentPool.take() // Acquire a first segment.
      head = result
      result.prev = result
      result.next = result
      return result
    }

    var tail = head!!.prev
    if (tail!!.limit + minimumCapacity > Segment.SIZE || !tail.owner) {
      tail = tail.push(SegmentPool.take()) // Append a new empty segment to fill up.
    }
    return tail
  }

  override fun write(source: Buffer, byteCount: Long) {
    var byteCount = byteCount
    // Move bytes from the head of the source buffer to the tail of this buffer
    // while balancing two conflicting goals: don't waste CPU and don't waste
    // memory.
    //
    //
    // Don't waste CPU (ie. don't copy data around).
    //
    // Copying large amounts of data is expensive. Instead, we prefer to
    // reassign entire segments from one buffer to the other.
    //
    //
    // Don't waste memory.
    //
    // As an invariant, adjacent pairs of segments in a buffer should be at
    // least 50% full, except for the head segment and the tail segment.
    //
    // The head segment cannot maintain the invariant because the application is
    // consuming bytes from this segment, decreasing its level.
    //
    // The tail segment cannot maintain the invariant because the application is
    // producing bytes, which may require new nearly-empty tail segments to be
    // appended.
    //
    //
    // Moving segments between buffers
    //
    // When writing one buffer to another, we prefer to reassign entire segments
    // over copying bytes into their most compact form. Suppose we have a buffer
    // with these segment levels [91%, 61%]. If we append a buffer with a
    // single [72%] segment, that yields [91%, 61%, 72%]. No bytes are copied.
    //
    // Or suppose we have a buffer with these segment levels: [100%, 2%], and we
    // want to append it to a buffer with these segment levels [99%, 3%]. This
    // operation will yield the following segments: [100%, 2%, 99%, 3%]. That
    // is, we do not spend time copying bytes around to achieve more efficient
    // memory use like [100%, 100%, 4%].
    //
    // When combining buffers, we will compact adjacent buffers when their
    // combined level doesn't exceed 100%. For example, when we start with
    // [100%, 40%] and append [30%, 80%], the result is [100%, 70%, 80%].
    //
    //
    // Splitting segments
    //
    // Occasionally we write only part of a source buffer to a sink buffer. For
    // example, given a sink [51%, 91%], we may want to write the first 30% of
    // a source [92%, 82%] to it. To simplify, we first transform the source to
    // an equivalent buffer [30%, 62%, 82%] and then move the head segment,
    // yielding sink [51%, 91%, 30%] and source [62%, 82%].

    require(source !== this) { "source == this" }
    checkOffsetAndCount(source.size, 0, byteCount)

    while (byteCount > 0L) {
      // Is a prefix of the source's head segment all that we need to move?
      if (byteCount < source.head!!.limit - source.head!!.pos) {
        val tail = if (head != null) head!!.prev else null
        if (tail != null && tail.owner &&
            byteCount + tail.limit - (if (tail.shared) 0 else tail.pos) <= Segment.SIZE) {
          // Our existing segments are sufficient. Move bytes from source's head to our tail.
          source.head!!.writeTo(tail, byteCount.toInt())
          source.size -= byteCount
          size += byteCount
          return
        } else {
          // We're going to need another segment. Split the source's head
          // segment in two, then move the first of those two to this buffer.
          source.head = source.head!!.split(byteCount.toInt())
        }
      }

      // Remove the source's head segment and append it to our tail.
      val segmentToMove = source.head
      val movedByteCount = (segmentToMove!!.limit - segmentToMove.pos).toLong()
      source.head = segmentToMove.pop()
      if (head == null) {
        head = segmentToMove
        segmentToMove.prev = segmentToMove
        segmentToMove.next = segmentToMove.prev
      } else {
        var tail = head!!.prev
        tail = tail!!.push(segmentToMove)
        tail.compact()
      }
      source.size -= movedByteCount
      size += movedByteCount
      byteCount -= movedByteCount
    }
  }

  override fun read(sink: Buffer, byteCount: Long): Long {
    var byteCount = byteCount
    require(byteCount >= 0) { "byteCount < 0: $byteCount" }
    if (size == 0L) return -1L
    if (byteCount > size) byteCount = size
    sink.write(this, byteCount)
    return byteCount
  }

  override fun indexOf(b: Byte) = indexOf(b, 0, Long.MAX_VALUE)

  /**
   * Invoke `lambda` with the segment and offset at `fromIndex`. Searches from the front or the back
   * depending on what's closer to `fromIndex`.
   */
  private inline fun <T> seek(fromIndex: Long, lambda: (Segment?, Long) -> T): T {
    var s: Segment = head ?: return lambda(null, -1L)

    if (size - fromIndex < fromIndex) {
      // We're scanning in the back half of this buffer. Find the segment starting at the back.
      var offset = size
      while (offset > fromIndex) {
        s = s.prev!!
        offset -= (s.limit - s.pos).toLong()
      }
      return lambda(s, offset)
    } else {
      // We're scanning in the front half of this buffer. Find the segment starting at the front.
      var offset = 0L
      while (true) {
        val nextOffset = offset + (s.limit - s.pos)
        if (nextOffset > fromIndex) break
        s = s.next!!
        offset = nextOffset
      }
      return lambda(s, offset)
    }
  }

  /**
   * Returns the index of `b` in this at or beyond `fromIndex`, or -1 if this buffer does not
   * contain `b` in that range.
   */
  override fun indexOf(b: Byte, fromIndex: Long) = indexOf(b, fromIndex, Long.MAX_VALUE)

  override fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long {
    var fromIndex = fromIndex
    var toIndex = toIndex
    require(fromIndex in 0..toIndex) { "size=$size fromIndex=$fromIndex toIndex=$toIndex" }

    if (toIndex > size) toIndex = size
    if (fromIndex == toIndex) return -1L

    seek(fromIndex) { s, offset ->
      var s = s ?: return -1L
      var offset = offset

      // Scan through the segments, searching for b.
      while (offset < toIndex) {
        val data = s.data
        val limit = minOf(s.limit.toLong(), s.pos + toIndex - offset).toInt()
        var pos = (s.pos + fromIndex - offset).toInt()
        while (pos < limit) {
          if (data[pos] == b) {
            return pos - s.pos + offset
          }
          pos++
        }

        // Not in this segment. Try the next one.
        offset += (s.limit - s.pos).toLong()
        fromIndex = offset
        s = s.next!!
      }

      return -1L
    }
  }

  @Throws(IOException::class)
  override fun indexOf(bytes: ByteString) = indexOf(bytes, 0)

  @Throws(IOException::class)
  override fun indexOf(bytes: ByteString, fromIndex: Long): Long {
    var fromIndex = fromIndex
    require(bytes.size > 0) { "bytes is empty" }
    require(fromIndex >= 0L) { "fromIndex < 0: $fromIndex" }

    seek(fromIndex) { s, offset ->
      var s = s ?: return -1L
      var offset = offset

      // Scan through the segments, searching for the lead byte. Each time that is found, delegate
      // to rangeEquals() to check for a complete match.
      val targetByteArray = bytes.internalArray()
      val b0 = targetByteArray[0]
      val bytesSize = bytes.size
      val resultLimit = size - bytesSize + 1L
      while (offset < resultLimit) {
        // Scan through the current segment.
        val data = s.data
        val segmentLimit = minOf(s.limit, s.pos + resultLimit - offset).toInt()
        for (pos in (s.pos + fromIndex - offset).toInt() until segmentLimit) {
          if (data[pos] == b0 && rangeEquals(s, pos + 1, targetByteArray, 1, bytesSize)) {
            return pos - s.pos + offset
          }
        }

        // Not in this segment. Try the next one.
        offset += (s.limit - s.pos).toLong()
        fromIndex = offset
        s = s.next!!
      }

      return -1L
    }
  }

  override fun indexOfElement(targetBytes: ByteString) = indexOfElement(targetBytes, 0L)

  override fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long {
    var fromIndex = fromIndex
    require(fromIndex >= 0L) { "fromIndex < 0: $fromIndex" }

    seek(fromIndex) { s, offset ->
      var s = s ?: return -1L
      var offset = offset

      // Special case searching for one of two bytes. This is a common case for tools like Moshi,
      // which search for pairs of chars like `\r` and `\n` or {@code `"` and `\`. The impact of this
      // optimization is a ~5x speedup for this case without a substantial cost to other cases.
      if (targetBytes.size == 2) {
        // Scan through the segments, searching for either of the two bytes.
        val b0 = targetBytes[0]
        val b1 = targetBytes[1]
        while (offset < size) {
          val data = s.data
          var pos = (s.pos + fromIndex - offset).toInt()
          val limit = s.limit
          while (pos < limit) {
            val b = data[pos].toInt()
            if (b == b0.toInt() || b == b1.toInt()) {
              return pos - s.pos + offset
            }
            pos++
          }

          // Not in this segment. Try the next one.
          offset += (s.limit - s.pos).toLong()
          fromIndex = offset
          s = s.next!!
        }
      } else {
        // Scan through the segments, searching for a byte that's also in the array.
        val targetByteArray = targetBytes.internalArray()
        while (offset < size) {
          val data = s.data
          var pos = (s.pos + fromIndex - offset).toInt()
          val limit = s.limit
          while (pos < limit) {
            val b = data[pos].toInt()
            for (t in targetByteArray) {
              if (b == t.toInt()) return pos - s.pos + offset
            }
            pos++
          }

          // Not in this segment. Try the next one.
          offset += (s.limit - s.pos).toLong()
          fromIndex = offset
          s = s.next!!
        }
      }

      return -1L
    }
  }

  override fun rangeEquals(offset: Long, bytes: ByteString) =
      rangeEquals(offset, bytes, 0, bytes.size)

  override fun rangeEquals(
    offset: Long,
    bytes: ByteString,
    bytesOffset: Int,
    byteCount: Int
  ): Boolean {
    if (offset < 0L ||
        bytesOffset < 0 ||
        byteCount < 0 ||
        size - offset < byteCount ||
        bytes.size - bytesOffset < byteCount) {
      return false
    }
    for (i in 0 until byteCount) {
      if (this[offset + i] != bytes[bytesOffset + i]) {
        return false
      }
    }
    return true
  }

  /**
   * Returns true if the range within this buffer starting at `segmentPos` in `segment` is equal to
   * `bytes[bytesOffset..bytesLimit)`.
   */
  private fun rangeEquals(
    segment: Segment,
    segmentPos: Int,
    bytes: ByteArray,
    bytesOffset: Int,
    bytesLimit: Int
  ): Boolean {
    var segment = segment
    var segmentPos = segmentPos
    var segmentLimit = segment.limit
    var data = segment.data

    var i = bytesOffset
    while (i < bytesLimit) {
      if (segmentPos == segmentLimit) {
        segment = segment.next!!
        data = segment.data
        segmentPos = segment.pos
        segmentLimit = segment.limit
      }

      if (data[segmentPos] != bytes[i]) {
        return false
      }

      segmentPos++
      i++
    }

    return true
  }

  override fun flush() {}

  override fun isOpen() = true

  override fun close() {}

  override fun timeout() = Timeout.NONE

  /** Returns the 128-bit MD5 hash of this buffer.  */
  fun md5() = digest("MD5")

  /** Returns the 160-bit SHA-1 hash of this buffer.  */
  fun sha1() = digest("SHA-1")

  /** Returns the 256-bit SHA-256 hash of this buffer.  */
  fun sha256() = digest("SHA-256")

  /** Returns the 512-bit SHA-512 hash of this buffer.  */
  fun sha512() = digest("SHA-512")

  private fun digest(algorithm: String): ByteString {
    val messageDigest = MessageDigest.getInstance(algorithm)
    head?.let { head ->
      messageDigest.update(head.data, head.pos, head.limit - head.pos)
      var s = head.next!!
      while (s !== head) {
        messageDigest.update(s.data, s.pos, s.limit - s.pos)
        s = s.next!!
      }
    }
    return ByteString(messageDigest.digest())
  }

  /** Returns the 160-bit SHA-1 HMAC of this buffer.  */
  fun hmacSha1(key: ByteString) = hmac("HmacSHA1", key)

  /** Returns the 256-bit SHA-256 HMAC of this buffer.  */
  fun hmacSha256(key: ByteString) = hmac("HmacSHA256", key)

  /** Returns the 512-bit SHA-512 HMAC of this buffer.  */
  fun hmacSha512(key: ByteString) = hmac("HmacSHA512", key)

  private fun hmac(algorithm: String, key: ByteString): ByteString {
    try {
      val mac = Mac.getInstance(algorithm)
      mac.init(SecretKeySpec(key.internalArray(), algorithm))
      head?.let { head ->
        mac.update(head.data, head.pos, head.limit - head.pos)
        var s = head.next!!
        while (s !== head) {
          mac.update(s.data, s.pos, s.limit - s.pos)
          s = s.next!!
        }
      }
      return ByteString(mac.doFinal())
    } catch (e: InvalidKeyException) {
      throw IllegalArgumentException(e)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Buffer) return false
    if (size != other.size) return false
    if (size == 0L) return true // Both buffers are empty.

    var sa = this.head!!
    var sb = other.head!!
    var posA = sa.pos
    var posB = sb.pos

    var pos = 0L
    var count: Long
    while (pos < size) {
      count = minOf(sa.limit - posA, sb.limit - posB).toLong()

      for (i in 0L until count) {
        if (sa.data[posA++] != sb.data[posB++]) return false
      }

      if (posA == sa.limit) {
        sa = sa.next!!
        posA = sa.pos
      }

      if (posB == sb.limit) {
        sb = sb.next!!
        posB = sb.pos
      }
      pos += count
    }

    return true
  }

  override fun hashCode(): Int {
    var s = head ?: return 0
    var result = 1
    do {
      var pos = s.pos
      val limit = s.limit
      while (pos < limit) {
        result = 31 * result + s.data[pos]
        pos++
      }
      s = s.next!!
    } while (s !== head)
    return result
  }

  /**
   * Returns a human-readable string that describes the contents of this buffer. Typically this
   * is a string like `[text=Hello]` or `[hex=0000ffff]`.
   */
  override fun toString() = snapshot().toString()

  /** Returns a deep copy of this buffer.  */
  public override fun clone(): Buffer {
    val result = Buffer()
    if (size == 0L) return result

    result.head = head!!.sharedCopy()
    result.head!!.prev = result.head
    result.head!!.next = result.head!!.prev
    var s = head!!.next
    while (s !== head) {
      result.head!!.prev!!.push(s!!.sharedCopy())
      s = s.next
    }
    result.size = size
    return result
  }

  /** Returns an immutable copy of this buffer as a byte string.  */
  fun snapshot(): ByteString {
    check(size <= Integer.MAX_VALUE) { "size > Integer.MAX_VALUE: $size" }
    return snapshot(size.toInt())
  }

  /** Returns an immutable copy of the first `byteCount` bytes of this buffer as a byte string. */
  fun snapshot(byteCount: Int): ByteString {
    return if (byteCount == 0) ByteString.EMPTY else SegmentedByteString.of(this, byteCount)
  }

  @JvmOverloads fun readUnsafe(unsafeCursor: UnsafeCursor = UnsafeCursor()): UnsafeCursor {
    check(unsafeCursor.buffer == null) { "already attached to a buffer" }

    unsafeCursor.buffer = this
    unsafeCursor.readWrite = false
    return unsafeCursor
  }

  @JvmOverloads
  fun readAndWriteUnsafe(unsafeCursor: UnsafeCursor = UnsafeCursor()): UnsafeCursor {
    check(unsafeCursor.buffer == null) { "already attached to a buffer" }

    unsafeCursor.buffer = this
    unsafeCursor.readWrite = true
    return unsafeCursor
  }

  @JvmName("-deprecated_getByte")
  @Deprecated(
      message = "moved to operator function",
      replaceWith = ReplaceWith(expression = "this[index]"),
      level = DeprecationLevel.ERROR)
  fun getByte(index: Long) = this[index]

  @JvmName("-deprecated_size")
  @Deprecated(
      message = "moved to val",
      replaceWith = ReplaceWith(expression = "size"),
      level = DeprecationLevel.ERROR)
  fun size() = size

  /**
   * A handle to the underlying data in a buffer. This handle is unsafe because it does not enforce
   * its own invariants. Instead, it assumes a careful user who has studied Okio's implementation
   * details and their consequences.
   *
   * Buffer Internals
   * ----------------
   *
   * Most code should use `Buffer` as a black box: a class that holds 0 or more bytes of
   * data with efficient APIs to append data to the end and to consume data from the front. Usually
   * this is also the most efficient way to use buffers because it allows Okio to employ several
   * optimizations, including:
   *
   *
   *  * **Fast Allocation:** Buffers use a shared pool of memory that is not zero-filled before use.
   *  * **Fast Resize:** A buffer's capacity can change without copying its contents.
   *  * **Fast Move:** Memory ownership can be reassigned from one buffer to another.
   *  * **Fast Copy:** Multiple buffers can share the same underlying memory.
   *  * **Fast Encoding and Decoding:** Common operations like UTF-8 encoding and decimal decoding
   *    do not require intermediate objects to be allocated.
   *
   * These optimizations all leverage the way Okio stores data internally. Okio Buffers are
   * implemented using a doubly-linked list of segments. Each segment is a contiguous range within a
   * 8 KiB `ByteArray`. Each segment has two indexes, `start`, the offset of the first byte of the
   * array containing application data, and `end`, the offset of the first byte beyond `start` whose
   * data is undefined.
   *
   * New buffers are empty and have no segments:
   *
   * ```
   *   val buffer = Buffer()
   * ```
   *
   * We append 7 bytes of data to the end of our empty buffer. Internally, the buffer allocates a
   * segment and writes its new data there. The lone segment has an 8 KiB byte array but only 7
   * bytes of data:
   *
   * ```
   * buffer.writeUtf8("sealion")
   *
   * // [ 's', 'e', 'a', 'l', 'i', 'o', 'n', '?', '?', '?', ...]
   * //    ^                                  ^
   * // start = 0                          end = 7
   * ```
   *
   * When we read 4 bytes of data from the buffer, it finds its first segment and returns that data
   * to us. As bytes are read the data is consumed. The segment tracks this by adjusting its
   * internal indices.
   *
   * ```
   * buffer.readUtf8(4) // "seal"
   *
   * // [ 's', 'e', 'a', 'l', 'i', 'o', 'n', '?', '?', '?', ...]
   * //                        ^              ^
   * //                     start = 4      end = 7
   * ```
   *
   * As we write data into a buffer we fill up its internal segments. When a write doesn't fit into
   * a buffer's last segment, additional segments are allocated and appended to the linked list of
   * segments. Each segment has its own start and end indexes tracking where the user's data begins
   * and ends.
   *
   * ```
   * val xoxo = new Buffer()
   * xoxo.writeUtf8("xo".repeat(5_000))
   *
   * // [ 'x', 'o', 'x', 'o', 'x', 'o', 'x', 'o', ..., 'x', 'o', 'x', 'o']
   * //    ^                                                               ^
   * // start = 0                                                      end = 8192
   * //
   * // [ 'x', 'o', 'x', 'o', ..., 'x', 'o', 'x', 'o', '?', '?', '?', ...]
   * //    ^                                            ^
   * // start = 0                                   end = 1808
   * ```
   *
   * The start index is always **inclusive** and the end index is always **exclusive**. The data
   * preceding the start index is undefined, and the data at and following the end index is
   * undefined.
   *
   * After the last byte of a segment has been read, that segment may be returned to an internal
   * segment pool. In addition to reducing the need to do garbage collection, segment pooling also
   * saves the JVM from needing to zero-fill byte arrays. Okio doesn't need to zero-fill its arrays
   * because it always writes memory before it reads it. But if you look at a segment in a debugger
   * you may see its effects. In this example, one of the "xoxo" segments above is reused in an
   * unrelated buffer:
   *
   * ```
   * val abc = new Buffer()
   * abc.writeUtf8("abc")
   *
   * // [ 'a', 'b', 'c', 'o', 'x', 'o', 'x', 'o', ...]
   * //    ^              ^
   * // start = 0     end = 3
   * ```
   *
   * There is an optimization in `Buffer.clone()` and other methods that allows two segments to
   * share the same underlying byte array. Clones can't write to the shared byte array; instead they
   * allocate a new (private) segment early.
   *
   * ```
   * val nana = new Buffer()
   * nana.writeUtf8("na".repeat(2_500))
   * nana.readUtf8(2) // "na"
   *
   * // [ 'n', 'a', 'n', 'a', ..., 'n', 'a', 'n', 'a', '?', '?', '?', ...]
   * //              ^                                  ^
   * //           start = 0                         end = 5000
   *
   * nana2 = nana.clone()
   * nana2.writeUtf8("batman")
   *
   * // [ 'n', 'a', 'n', 'a', ..., 'n', 'a', 'n', 'a', '?', '?', '?', ...]
   * //              ^                                  ^
   * //           start = 0                         end = 5000
   * //
   * // [ 'b', 'a', 't', 'm', 'a', 'n', '?', '?', '?', ...]
   * //    ^                             ^
   * //  start = 0                    end = 7
   * ```
   *
   * Segments are not shared when the shared region is small (ie. less than 1 KiB). This is intended
   * to prevent fragmentation in sharing-heavy use cases.
   *
   * Unsafe Cursor API
   * -----------------
   *
   * This class exposes privileged access to the internal byte arrays of a buffer. A cursor either
   * references the data of a single segment, it is before the first segment (`offset == -1`), or it
   * is after the last segment (`offset == buffer.size`).
   *
   * Call [UnsafeCursor.seek] to move the cursor to the segment that contains a specified offset.
   * After seeking, [UnsafeCursor.data] references the segment's internal byte array,
   * [UnsafeCursor.start] is the segment's start and [UnsafeCursor.end] is its end.
   *
   *
   * Call [UnsafeCursor.next] to advance the cursor to the next segment. This returns -1 if there
   * are no further segments in the buffer.
   *
   *
   * Use [Buffer.readUnsafe] to create a cursor to read buffer data and [Buffer.readAndWriteUnsafe]
   * to create a cursor to read and write buffer data. In either case, always call
   * [UnsafeCursor.close] when done with a cursor. This is convenient with Kotlin's
   * [use] extension function. In this example we read all of the bytes in a buffer into a byte
   * array:
   *
   * ```
   * val bufferBytes = ByteArray(buffer.size.toInt())
   *
   * buffer.readUnsafe().use { cursor ->
   *   while (cursor.next() != -1) {
   *     System.arraycopy(cursor.data, cursor.start,
   *         bufferBytes, cursor.offset.toInt(), cursor.end - cursor.start);
   *   }
   * }
   * ```
   *
   * Change the capacity of a buffer with [.resizeBuffer]. This is only permitted for
   * read+write cursors. The buffer's size always changes from the end: shrinking it removes bytes
   * from the end; growing it adds capacity to the end.
   *
   * Warnings
   * --------
   *
   * Most application developers should avoid this API. Those that must use this API should
   * respect these warnings.
   *
   * **Don't mutate a cursor.** This class has public, non-final fields because that is convenient
   * for low-level I/O frameworks. Never assign values to these fields; instead use the cursor API
   * to adjust these.
   *
   * **Never mutate `data` unless you have read+write access.** You are on the honor system to never
   * write the buffer in read-only mode. Read-only mode may be more efficient than read+write mode
   * because it does not need to make private copies of shared segments.
   *
   * **Only access data in `[start..end)`.** Other data in the byte array is undefined! It may
   * contain private or sensitive data from other parts of your process.
   *
   * **Always fill the new capacity when you grow a buffer.** New capacity is not zero-filled and
   * may contain data from other parts of your process. Avoid leaking this information by always
   * writing something to the newly-allocated capacity. Do not assume that new capacity will be
   * filled with `0`; it will not be.
   *
   * **Do not access a buffer while is being accessed by a cursor.** Even simple read-only
   * operations like [Buffer.clone] are unsafe because they mark segments as shared.
   *
   * **Do not hard-code the segment size in your application.** It is possible that segment sizes
   * will change with advances in hardware. Future versions of Okio may even have heterogeneous
   * segment sizes.
   *
   * These warnings are intended to help you to use this API safely. It's here for developers
   * that need absolutely the most throughput. Since that's you, here's one final performance tip.
   * You can reuse instances of this class if you like. Use the overloads of [Buffer.readUnsafe] and
   * [Buffer.readAndWriteUnsafe] that take a cursor and close it after use.
   */
  class UnsafeCursor : Closeable {
    @JvmField var buffer: Buffer? = null
    @JvmField var readWrite: Boolean = false

    private var segment: Segment? = null
    @JvmField var offset = -1L
    @JvmField var data: ByteArray? = null
    @JvmField var start = -1
    @JvmField var end = -1

    /**
     * Seeks to the next range of bytes, advancing the offset by `end - start`. Returns the size of
     * the readable range (at least 1), or -1 if we have reached the end of the buffer and there are
     * no more bytes to read.
     */
    fun next(): Int {
      check(offset != buffer!!.size) { "no more bytes" }
      return if (offset == -1L) seek(0L) else seek(offset + (end - start))
    }

    /**
     * Reposition the cursor so that the data at `offset` is readable at `data[start]`.
     * Returns the number of bytes readable in `data` (at least 1), or -1 if there are no data
     * to read.
     */
    fun seek(offset: Long): Int {
      val buffer = checkNotNull(buffer) { "not attached to a buffer" }
      if (offset < -1 || offset > buffer.size) {
        throw ArrayIndexOutOfBoundsException(
            String.format("offset=%s > size=%s", offset, buffer.size))
      }

      if (offset == -1L || offset == buffer.size) {
        this.segment = null
        this.offset = offset
        this.data = null
        this.start = -1
        this.end = -1
        return -1
      }

      // Navigate to the segment that contains `offset`. Start from our current segment if possible.
      var min = 0L
      var max = buffer.size
      var head = buffer.head
      var tail = buffer.head
      if (this.segment != null) {
        val segmentOffset = this.offset - (this.start - this.segment!!.pos)
        if (segmentOffset > offset) {
          // Set the cursor segment to be the 'end'
          max = segmentOffset
          tail = this.segment
        } else {
          // Set the cursor segment to be the 'beginning'
          min = segmentOffset
          head = this.segment
        }
      }

      var next: Segment?
      var nextOffset: Long
      if (max - offset > offset - min) {
        // Start at the 'beginning' and search forwards
        next = head
        nextOffset = min
        while (offset >= nextOffset + (next!!.limit - next.pos)) {
          nextOffset += (next.limit - next.pos).toLong()
          next = next.next
        }
      } else {
        // Start at the 'end' and search backwards
        next = tail
        nextOffset = max
        while (nextOffset > offset) {
          next = next!!.prev
          nextOffset -= (next!!.limit - next.pos).toLong()
        }
      }

      // If we're going to write and our segment is shared, swap it for a read-write one.
      if (readWrite && next!!.shared) {
        val unsharedNext = next.unsharedCopy()
        if (buffer.head === next) {
          buffer.head = unsharedNext
        }
        next = next.push(unsharedNext)
        next.prev!!.pop()
      }

      // Update this cursor to the requested offset within the found segment.
      this.segment = next
      this.offset = offset
      this.data = next!!.data
      this.start = next.pos + (offset - nextOffset).toInt()
      this.end = next.limit
      return end - start
    }

    /**
     * Change the size of the buffer so that it equals `newSize` by either adding new
     * capacity at the end or truncating the buffer at the end. Newly added capacity may span
     * multiple segments.
     *
     * As a side-effect this cursor will [seek][UnsafeCursor.seek]. If the buffer is being enlarged
     * it will move [UnsafeCursor.offset] to the first byte of newly-added capacity. This is the
     * size of the buffer prior to the `resizeBuffer()` call. If the buffer is being shrunk it will move
     * [UnsafeCursor.offset] to the end of the buffer.
     *
     * Warning: it is the caller’s responsibility to write new data to every byte of the
     * newly-allocated capacity. Failure to do so may cause serious security problems as the data
     * in the returned buffers is not zero filled. Buffers may contain dirty pooled segments that
     * hold very sensitive data from other parts of the current process.
     *
     * @return the previous size of the buffer.
     */
    fun resizeBuffer(newSize: Long): Long {
      val buffer = checkNotNull(buffer) { "not attached to a buffer" }
      check(readWrite) { "resizeBuffer() only permitted for read/write buffers" }

      val oldSize = buffer.size
      if (newSize <= oldSize) {
        require(newSize >= 0L) { "newSize < 0: $newSize" }
        // Shrink the buffer by either shrinking segments or removing them.
        var bytesToSubtract = oldSize - newSize
        while (bytesToSubtract > 0L) {
          val tail = buffer.head!!.prev
          val tailSize = tail!!.limit - tail.pos
          if (tailSize <= bytesToSubtract) {
            buffer.head = tail.pop()
            SegmentPool.recycle(tail)
            bytesToSubtract -= tailSize.toLong()
          } else {
            tail.limit -= bytesToSubtract.toInt()
            break
          }
        }
        // Seek to the end.
        this.segment = null
        this.offset = newSize
        this.data = null
        this.start = -1
        this.end = -1
      } else if (newSize > oldSize) {
        // Enlarge the buffer by either enlarging segments or adding them.
        var needsToSeek = true
        var bytesToAdd = newSize - oldSize
        while (bytesToAdd > 0L) {
          val tail = buffer.writableSegment(1)
          val segmentBytesToAdd = minOf(bytesToAdd, Segment.SIZE - tail.limit).toInt()
          tail.limit += segmentBytesToAdd
          bytesToAdd -= segmentBytesToAdd.toLong()

          // If this is the first segment we're adding, seek to it.
          if (needsToSeek) {
            this.segment = tail
            this.offset = oldSize
            this.data = tail.data
            this.start = tail.limit - segmentBytesToAdd
            this.end = tail.limit
            needsToSeek = false
          }
        }
      }

      buffer.size = newSize

      return oldSize
    }

    /**
     * Grow the buffer by adding a **contiguous range** of capacity in a single segment. This adds
     * at least `minByteCount` bytes but may add up to a full segment of additional capacity.
     *
     * As a side-effect this cursor will [seek][UnsafeCursor.seek]. It will move
     * [offset][UnsafeCursor.offset] to the first byte of newly-added capacity. This is the size of
     * the buffer prior to the `expandBuffer()` call.
     *
     * If `minByteCount` bytes are available in the buffer's current tail segment that will
     * be used; otherwise another segment will be allocated and appended. In either case this
     * returns the number of bytes of capacity added to this buffer.
     *
     * Warning: it is the caller’s responsibility to either write new data to every byte of the
     * newly-allocated capacity, or to [shrink][UnsafeCursor.resizeBuffer] the buffer to the data
     * written. Failure to do so may cause serious security problems as the data in the returned
     * buffers is not zero filled. Buffers may contain dirty pooled segments that hold very
     * sensitive data from other parts of the current process.
     *
     * @param minByteCount the size of the contiguous capacity. Must be positive and not greater
     * than the capacity size of a single segment (8 KiB).
     * @return the number of bytes expanded by. Not less than `minByteCount`.
     */
    fun expandBuffer(minByteCount: Int): Long {
      require(minByteCount > 0) { "minByteCount <= 0: $minByteCount" }
      require(minByteCount <= Segment.SIZE) { "minByteCount > Segment.SIZE: $minByteCount" }
      val buffer = checkNotNull(buffer) { "not attached to a buffer" }
      check(readWrite) { "expandBuffer() only permitted for read/write buffers" }

      val oldSize = buffer.size
      val tail = buffer.writableSegment(minByteCount)
      val result = Segment.SIZE - tail.limit
      tail.limit = Segment.SIZE
      buffer.size = oldSize + result

      // Seek to the old size.
      this.segment = tail
      this.offset = oldSize
      this.data = tail.data
      this.start = Segment.SIZE - result
      this.end = Segment.SIZE

      return result.toLong()
    }

    override fun close() {
      // TODO(jwilson): use edit counts or other information to track unexpected changes?
      check(buffer != null) { "not attached to a buffer" }

      buffer = null
      segment = null
      offset = -1L
      data = null
      start = -1
      end = -1
    }
  }

  companion object {
    private val DIGITS = "0123456789abcdef".toByteArray()
  }
}