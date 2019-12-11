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

import okio.internal.commonClear
import okio.internal.commonCompleteSegmentByteCount
import okio.internal.commonCopy
import okio.internal.commonCopyTo
import okio.internal.commonEquals
import okio.internal.commonGet
import okio.internal.commonHashCode
import okio.internal.commonIndexOf
import okio.internal.commonIndexOfElement
import okio.internal.commonRangeEquals
import okio.internal.commonRead
import okio.internal.commonReadAll
import okio.internal.commonReadByte
import okio.internal.commonReadByteArray
import okio.internal.commonReadByteString
import okio.internal.commonReadDecimalLong
import okio.internal.commonReadFully
import okio.internal.commonReadHexadecimalUnsignedLong
import okio.internal.commonReadInt
import okio.internal.commonReadLong
import okio.internal.commonReadShort
import okio.internal.commonReadUtf8CodePoint
import okio.internal.commonReadUtf8Line
import okio.internal.commonReadUtf8LineStrict
import okio.internal.commonSelect
import okio.internal.commonSkip
import okio.internal.commonSnapshot
import okio.internal.commonWritableSegment
import okio.internal.commonWrite
import okio.internal.commonWriteAll
import okio.internal.commonWriteByte
import okio.internal.commonWriteDecimalLong
import okio.internal.commonWriteHexadecimalUnsignedLong
import okio.internal.commonWriteInt
import okio.internal.commonWriteLong
import okio.internal.commonWriteShort
import okio.internal.commonWriteUtf8
import okio.internal.commonWriteUtf8CodePoint
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

actual class Buffer : BufferedSource, BufferedSink, Cloneable, ByteChannel {
  @JvmField internal actual var head: Segment? = null

  @get:JvmName("size")
  actual var size: Long = 0L
    internal set

  override fun buffer() = this

  actual override val buffer get() = this

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

  actual override fun emitCompleteSegments() = this // Nowhere to emit to!

  actual override fun emit() = this // Nowhere to emit to!

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

  actual fun copyTo(
    out: Buffer,
    offset: Long,
    byteCount: Long
  ): Buffer = commonCopyTo(out, offset, byteCount)

  actual fun copyTo(
    out: Buffer,
    offset: Long
  ): Buffer = copyTo(out, offset, size - offset)

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
        if (tail.pos == tail.limit) {
          // We allocated a tail segment, but didn't end up needing it. Recycle!
          head = tail.pop()
          SegmentPool.recycle(tail)
        }
        if (forever) return
        throw EOFException()
      }
      tail.limit += bytesRead
      size += bytesRead.toLong()
      byteCount -= bytesRead.toLong()
    }
  }

  actual fun completeSegmentByteCount(): Long = commonCompleteSegmentByteCount()

  @Throws(EOFException::class)
  override fun readByte(): Byte = commonReadByte()

  @JvmName("getByte")
  actual operator fun get(pos: Long): Byte = commonGet(pos)

  @Throws(EOFException::class)
  override fun readShort(): Short = commonReadShort()

  @Throws(EOFException::class)
  override fun readInt(): Int = commonReadInt()

  @Throws(EOFException::class)
  override fun readLong(): Long = commonReadLong()

  @Throws(EOFException::class)
  override fun readShortLe() = readShort().reverseBytes()

  @Throws(EOFException::class)
  override fun readIntLe() = readInt().reverseBytes()

  @Throws(EOFException::class)
  override fun readLongLe() = readLong().reverseBytes()

  @Throws(EOFException::class)
  override fun readDecimalLong(): Long = commonReadDecimalLong()

  @Throws(EOFException::class)
  override fun readHexadecimalUnsignedLong(): Long = commonReadHexadecimalUnsignedLong()

  override fun readByteString(): ByteString = commonReadByteString()

  @Throws(EOFException::class)
  override fun readByteString(byteCount: Long) = commonReadByteString(byteCount)

  override fun select(options: Options): Int = commonSelect(options)

  @Throws(EOFException::class)
  override fun readFully(sink: Buffer, byteCount: Long): Unit = commonReadFully(sink, byteCount)

  @Throws(IOException::class)
  override fun readAll(sink: Sink): Long = commonReadAll(sink)

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
  override fun readUtf8Line(): String? = commonReadUtf8Line()

  @Throws(EOFException::class)
  override fun readUtf8LineStrict() = readUtf8LineStrict(Long.MAX_VALUE)

  @Throws(EOFException::class)
  override fun readUtf8LineStrict(limit: Long): String = commonReadUtf8LineStrict(limit)

  @Throws(EOFException::class)
  override fun readUtf8CodePoint(): Int = commonReadUtf8CodePoint()

  override fun readByteArray() = commonReadByteArray()

  @Throws(EOFException::class)
  override fun readByteArray(byteCount: Long): ByteArray = commonReadByteArray(byteCount)

  override fun read(sink: ByteArray) = commonRead(sink)

  @Throws(EOFException::class)
  override fun readFully(sink: ByteArray) = commonReadFully(sink)

  override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int =
    commonRead(sink, offset, byteCount)

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

  actual fun clear() = commonClear()

  @Throws(EOFException::class)
  actual override fun skip(byteCount: Long) = commonSkip(byteCount)

  actual override fun write(byteString: ByteString): Buffer = commonWrite(byteString)

  actual override fun write(byteString: ByteString, offset: Int, byteCount: Int) =
    commonWrite(byteString, offset, byteCount)

  actual override fun writeUtf8(string: String): Buffer = writeUtf8(string, 0, string.length)

  actual override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): Buffer =
    commonWriteUtf8(string, beginIndex, endIndex)

  actual override fun writeUtf8CodePoint(codePoint: Int): Buffer =
    commonWriteUtf8CodePoint(codePoint)

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

  actual override fun write(source: ByteArray): Buffer = commonWrite(source)

  actual override fun write(
    source: ByteArray,
    offset: Int,
    byteCount: Int
  ): Buffer = commonWrite(source, offset, byteCount)

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
  override fun writeAll(source: Source): Long = commonWriteAll(source)

  @Throws(IOException::class)
  actual override fun write(source: Source, byteCount: Long): Buffer =
    commonWrite(source, byteCount)

  actual override fun writeByte(b: Int): Buffer = commonWriteByte(b)

  actual override fun writeShort(s: Int): Buffer = commonWriteShort(s)

  actual override fun writeShortLe(s: Int) = writeShort(s.toShort().reverseBytes().toInt())

  actual override fun writeInt(i: Int): Buffer = commonWriteInt(i)

  actual override fun writeIntLe(i: Int) = writeInt(i.reverseBytes())

  actual override fun writeLong(v: Long): Buffer = commonWriteLong(v)

  actual override fun writeLongLe(v: Long) = writeLong(v.reverseBytes())

  actual override fun writeDecimalLong(v: Long): Buffer = commonWriteDecimalLong(v)

  actual override fun writeHexadecimalUnsignedLong(v: Long): Buffer =
    commonWriteHexadecimalUnsignedLong(v)

  internal actual fun writableSegment(minimumCapacity: Int): Segment =
    commonWritableSegment(minimumCapacity)

  override fun write(source: Buffer, byteCount: Long): Unit = commonWrite(source, byteCount)

  override fun read(sink: Buffer, byteCount: Long): Long = commonRead(sink, byteCount)

  override fun indexOf(b: Byte) = indexOf(b, 0, Long.MAX_VALUE)

  /**
   * Returns the index of `b` in this at or beyond `fromIndex`, or -1 if this buffer does not
   * contain `b` in that range.
   */
  override fun indexOf(b: Byte, fromIndex: Long) = indexOf(b, fromIndex, Long.MAX_VALUE)

  override fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long = commonIndexOf(b, fromIndex, toIndex)

  @Throws(IOException::class)
  override fun indexOf(bytes: ByteString): Long = indexOf(bytes, 0)

  @Throws(IOException::class)
  override fun indexOf(bytes: ByteString, fromIndex: Long): Long = commonIndexOf(bytes, fromIndex)

  override fun indexOfElement(targetBytes: ByteString) = indexOfElement(targetBytes, 0L)

  override fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long =
    commonIndexOfElement(targetBytes, fromIndex)

  override fun rangeEquals(offset: Long, bytes: ByteString) =
    rangeEquals(offset, bytes, 0, bytes.size)

  override fun rangeEquals(
    offset: Long,
    bytes: ByteString,
    bytesOffset: Int,
    byteCount: Int
  ): Boolean = commonRangeEquals(offset, bytes, bytesOffset, byteCount)

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

  override fun equals(other: Any?): Boolean = commonEquals(other)

  override fun hashCode(): Int = commonHashCode()

  /**
   * Returns a human-readable string that describes the contents of this buffer. Typically this
   * is a string like `[text=Hello]` or `[hex=0000ffff]`.
   */
  override fun toString() = snapshot().toString()

  actual fun copy(): Buffer = commonCopy()

  /** Returns a deep copy of this buffer. */
  public override fun clone(): Buffer = copy()

  actual fun snapshot(): ByteString = commonSnapshot()

  actual fun snapshot(byteCount: Int): ByteString = commonSnapshot(byteCount)

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
   * //           start = 2                         end = 5000
   *
   * nana2 = nana.clone()
   * nana2.writeUtf8("batman")
   *
   * // [ 'n', 'a', 'n', 'a', ..., 'n', 'a', 'n', 'a', '?', '?', '?', ...]
   * //              ^                                  ^
   * //           start = 2                         end = 5000
   * //
   * // [ 'b', 'a', 't', 'm', 'a', 'n', '?', '?', '?', ...]
   * //    ^                             ^
   * //  start = 0                    end = 6
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
   * Change the capacity of a buffer with [resizeBuffer]. This is only permitted for
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
}
