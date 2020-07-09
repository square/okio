/*
 * Copyright (C) 2019 Square, Inc.
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
expect class Buffer() : BufferedSource, BufferedSink {
  internal var head: Segment?

  var size: Long
    internal set

  override val buffer: Buffer

  override fun emitCompleteSegments(): Buffer

  override fun emit(): Buffer

  /** Copy `byteCount` bytes from this, starting at `offset`, to `out`.  */
  fun copyTo(
    out: Buffer,
    offset: Long = 0L,
    byteCount: Long
  ): Buffer

  /**
   * Overload of [copyTo] with byteCount = size - offset, work around for
   *  https://youtrack.jetbrains.com/issue/KT-30847
   */
  fun copyTo(
    out: Buffer,
    offset: Long = 0L
  ): Buffer

  /**
   * Returns the number of bytes in segments that are not writable. This is the number of bytes that
   * can be flushed immediately to an underlying sink without harming throughput.
   */
  fun completeSegmentByteCount(): Long

  /** Returns the byte at `pos`. */
  operator fun get(pos: Long): Byte

  /**
   * Discards all bytes in this buffer. Calling this method when you're done with a buffer will
   * return its segments to the pool.
   */
  fun clear()

  /** Discards `byteCount` bytes from the head of this buffer.  */
  override fun skip(byteCount: Long)

  override fun write(byteString: ByteString): Buffer

  override fun write(byteString: ByteString, offset: Int, byteCount: Int): Buffer

  override fun writeUtf8(string: String): Buffer

  override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): Buffer

  override fun writeUtf8CodePoint(codePoint: Int): Buffer

  override fun write(source: ByteArray): Buffer

  /**
   * Returns a tail segment that we can write at least `minimumCapacity`
   * bytes to, creating it if necessary.
   */
  internal fun writableSegment(minimumCapacity: Int): Segment

  override fun write(source: ByteArray, offset: Int, byteCount: Int): Buffer

  override fun write(source: Source, byteCount: Long): Buffer

  override fun writeByte(b: Int): Buffer

  override fun writeShort(s: Int): Buffer

  override fun writeShortLe(s: Int): Buffer

  override fun writeInt(i: Int): Buffer

  override fun writeIntLe(i: Int): Buffer

  override fun writeLong(v: Long): Buffer

  override fun writeLongLe(v: Long): Buffer

  override fun writeDecimalLong(v: Long): Buffer

  override fun writeHexadecimalUnsignedLong(v: Long): Buffer

  /** Returns a deep copy of this buffer.  */
  fun copy(): Buffer

  /** Returns an immutable copy of this buffer as a byte string.  */
  fun snapshot(): ByteString

  /** Returns an immutable copy of the first `byteCount` bytes of this buffer as a byte string. */
  fun snapshot(byteCount: Int): ByteString
}

fun Buffer.writeBase64(string: String) {
  // Ignore trailing '=' padding and whitespace from the input.
  var limit = string.length
  while (limit > 0) {
    val c = string[limit - 1]
    if (c != '=' && c != '\n' && c != '\r' && c != ' ' && c != '\t') {
      break
    }
    limit--
  }

  var inCount = 0
  var word = 0
  var pos = 0
  var s = head
  while (pos < limit) {
    val c = string[pos++]
    val bits: Int
    if (c in 'A'..'Z') {
      // char ASCII value
      //  A    65    0
      //  Z    90    25 (ASCII - 65)
      bits = c.toInt() - 65
    } else if (c in 'a'..'z') {
      // char ASCII value
      //  a    97    26
      //  z    122   51 (ASCII - 71)
      bits = c.toInt() - 71
    } else if (c in '0'..'9') {
      // char ASCII value
      //  0    48    52
      //  9    57    61 (ASCII + 4)
      bits = c.toInt() + 4
    } else if (c == '+' || c == '-') {
      bits = 62
    } else if (c == '/' || c == '_') {
      bits = 63
    } else if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
      continue
    } else {
      throw IllegalArgumentException("Invalid Base64") // TODO: Dedicated exception? IOException?
    }

    // Append this char's 6 bits to the word.
    word = word shl 6 or bits

    // For every 4 chars of input, we accumulate 24 bits of output. Emit 3 bytes.
    inCount++
    if (inCount % 4 == 0) {
      if (s == null || s.limit + 3 > Segment.SIZE) {
        // For simplicity, don't try to write blocks across different segments, allocate new segment when current doesn't have enough capacity
        s = writableSegment(3)
      }
      val data = s.data
      var i = s.limit
      data[i++] = (word shr 16).toByte()
      data[i++] = (word shr 8).toByte()
      data[i++] = word.toByte()
      s.limit = i
      size += 3
    }
  }

  val lastWordChars = inCount % 4
  when (lastWordChars) {
    1 -> {
      // We read 1 char followed by "===". But 6 bits is a truncated byte! Fail.
      throw IllegalArgumentException("Invalid Base64") // TODO: Dedicated exception? IOException?
    }
    2 -> {
      // We read 2 chars followed by "==". Emit 1 byte with 8 of those 12 bits.
      if (s == null || s.limit + 1 > Segment.SIZE) {
        s = writableSegment(1)
      }
      word = word shl 12
      s.data[s.limit++] = (word shr 16).toByte()
      size += 1
    }
    3 -> {
      // We read 3 chars, followed by "=". Emit 2 bytes for 16 of those 18 bits.
      if (s == null || s.limit + 2 > Segment.SIZE) {
        s = writableSegment(2)
      }
      word = word shl 6
      val data = s.data
      var i = s.limit
      data[i++] = (word shr 16).toByte()
      data[i++] = (word shr 8).toByte()
      s.limit = i
      size += 2
    }
  }
}

fun Buffer.readBase64(): String =
  readBase64(BASE64)

fun Buffer.readBase64Url(): String =
  readBase64(BASE64_URL_SAFE)

private fun Buffer.readBase64(map: String = BASE64): String {
  val length = ((size + 2) / 3 * 4).toInt() // TODO: Prevent Int overflow / arithmetic overflow ?
  val out = CharArray(length)
  var index = 0
  while (size >= 3) {
    val s = head!!
    val segmentSize = s.limit - s.pos
    if (segmentSize > 3) {
      // Read all complete blocks from head segment
      val data = s.data
      val end = s.limit - segmentSize % 3
      var i = s.pos
      while (i < end) {
        val b0 = data[i++].toInt()
        val b1 = data[i++].toInt()
        val b2 = data[i++].toInt()
        out[index++] = map[(b0 and 0xff shr 2)]
        out[index++] = map[(b0 and 0x03 shl 4) or (b1 and 0xff shr 4)]
        out[index++] = map[(b1 and 0x0f shl 2) or (b2 and 0xff shr 6)]
        out[index++] = map[(b2 and 0x3f)]
      }
      size -= end - s.pos
      if (end == s.limit) {
        head = s.pop()
        SegmentPool.recycle(s)
      } else {
        s.pos = end
      }
    } else {
      // Read next block, which is spread over multiple segments
      val b0 = readByte().toInt()
      val b1 = readByte().toInt()
      val b2 = readByte().toInt()
      out[index++] = map[(b0 and 0xff shr 2)]
      out[index++] = map[(b0 and 0x03 shl 4) or (b1 and 0xff shr 4)]
      out[index++] = map[(b1 and 0x0f shl 2) or (b2 and 0xff shr 6)]
      out[index++] = map[(b2 and 0x3f)]
    }
  }
  when (size) {
    1L -> {
      val b0 = readByte().toInt()
      out[index++] = map[b0 and 0xff shr 2]
      out[index++] = map[b0 and 0x03 shl 4]
      out[index++] = '='
      out[index] = '='
    }
    2L -> {
      val b0 = readByte().toInt()
      val b1 = readByte().toInt()
      out[index++] = map[(b0 and 0xff shr 2)]
      out[index++] = map[(b0 and 0x03 shl 4) or (b1 and 0xff shr 4)]
      out[index++] = map[(b1 and 0x0f shl 2)]
      out[index] = '='
    }
  }
  return out.concatToString()
}
