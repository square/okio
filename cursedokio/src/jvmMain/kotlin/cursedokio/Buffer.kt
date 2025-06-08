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
package cursedokio

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
import cursedokio.internal.commonClear
import cursedokio.internal.commonClose
import cursedokio.internal.commonCompleteSegmentByteCount
import cursedokio.internal.commonCopy
import cursedokio.internal.commonCopyTo
import cursedokio.internal.commonEquals
import cursedokio.internal.commonExpandBuffer
import cursedokio.internal.commonGet
import cursedokio.internal.commonHashCode
import cursedokio.internal.commonIndexOf
import cursedokio.internal.commonIndexOfElement
import cursedokio.internal.commonNext
import cursedokio.internal.commonRangeEquals
import cursedokio.internal.commonRead
import cursedokio.internal.commonReadAll
import cursedokio.internal.commonReadAndWriteUnsafe
import cursedokio.internal.commonReadByte
import cursedokio.internal.commonReadByteArray
import cursedokio.internal.commonReadByteString
import cursedokio.internal.commonReadDecimalLong
import cursedokio.internal.commonReadFully
import cursedokio.internal.commonReadHexadecimalUnsignedLong
import cursedokio.internal.commonReadInt
import cursedokio.internal.commonReadLong
import cursedokio.internal.commonReadShort
import cursedokio.internal.commonReadUnsafe
import cursedokio.internal.commonReadUtf8CodePoint
import cursedokio.internal.commonReadUtf8Line
import cursedokio.internal.commonReadUtf8LineStrict
import cursedokio.internal.commonResizeBuffer
import cursedokio.internal.commonSeek
import cursedokio.internal.commonSelect
import cursedokio.internal.commonSkip
import cursedokio.internal.commonSnapshot
import cursedokio.internal.commonWritableSegment
import cursedokio.internal.commonWrite
import cursedokio.internal.commonWriteAll
import cursedokio.internal.commonWriteByte
import cursedokio.internal.commonWriteDecimalLong
import cursedokio.internal.commonWriteHexadecimalUnsignedLong
import cursedokio.internal.commonWriteInt
import cursedokio.internal.commonWriteLong
import cursedokio.internal.commonWriteShort
import cursedokio.internal.commonWriteUtf8
import cursedokio.internal.commonWriteUtf8CodePoint

actual class Buffer : BufferedSource, BufferedSink, Cloneable {
  @JvmField internal actual var head: Segment? = null

  @get:JvmName("size")
  actual var size: Long = 0L
    internal set

  override fun buffer() = this

  actual override val buffer get() = this

//  override fun outputStream(): OutputStream {
//    return object : OutputStream() {
//      override fun write(b: Int) {
//        writeByte(b)
//      }
//
//      override fun write(data: ByteArray, offset: Int, byteCount: Int) {
//        this@Buffer.write(data, offset, byteCount)
//      }
//
//      override fun flush() {}
//
//      override fun close() {}
//
//      override fun toString(): String = "${this@Buffer}.outputStream()"
//    }
//  }

  actual override suspend fun emitCompleteSegments() = this // Nowhere to emit to!

  actual override suspend fun emit() = this // Nowhere to emit to!

  actual override suspend fun exhausted() = size == 0L

  @Throws(EOFException::class)
  actual override suspend fun require(byteCount: Long) {
    if (size < byteCount) throw EOFException()
  }

  actual override suspend fun request(byteCount: Long) = size >= byteCount

  actual override fun peek(): BufferedSource {
    return PeekSource(this).buffer()
  }

//  override fun inputStream(): InputStream {
//    return object : InputStream() {
//      override fun read(): Int {
//        return if (size > 0L) {
//          readByte() and 0xff
//        } else {
//          -1
//        }
//      }
//
//      override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int {
//        return this@Buffer.read(sink, offset, byteCount)
//      }
//
//      override fun available() = minOf(size, Integer.MAX_VALUE).toInt()
//
//      override fun close() {}
//
//      override fun toString() = "${this@Buffer}.inputStream()"
//    }
//  }

  /** Copy `byteCount` bytes from this, starting at `offset`, to `out`. */
  @Throws(IOException::class)
  @JvmOverloads
  fun copyTo(
    out: OutputStream,
    offset: Long = 0L,
    byteCount: Long = size - offset,
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
    byteCount: Long,
  ): Buffer = commonCopyTo(out, offset, byteCount)

  actual fun copyTo(
    out: Buffer,
    offset: Long,
  ): Buffer = copyTo(out, offset, size - offset)

  /** Write `byteCount` bytes from this to `out`. */
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

  /** Read and exhaust bytes from `input` into this. */
  @Throws(IOException::class)
  fun readFrom(input: InputStream): Buffer {
    readFrom(input, Long.MAX_VALUE, true)
    return this
  }

  /** Read `byteCount` bytes from `input` into this. */
  @Throws(IOException::class)
  fun readFrom(input: InputStream, byteCount: Long): Buffer {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
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
  actual override suspend fun readByte(): Byte = commonReadByte()

  @JvmName("getByte")
  actual operator fun get(pos: Long): Byte = commonGet(pos)

  @Throws(EOFException::class)
  actual override suspend fun readShort(): Short = commonReadShort()

  @Throws(EOFException::class)
  actual override suspend fun readInt(): Int = commonReadInt()

  @Throws(EOFException::class)
  actual override suspend fun readLong(): Long = commonReadLong()

  @Throws(EOFException::class)
  actual override suspend fun readShortLe() = readShort().reverseBytes()

  @Throws(EOFException::class)
  actual override suspend fun readIntLe() = readInt().reverseBytes()

  @Throws(EOFException::class)
  actual override suspend fun readLongLe() = readLong().reverseBytes()

  @Throws(EOFException::class)
  actual override suspend fun readDecimalLong(): Long = commonReadDecimalLong()

  @Throws(EOFException::class)
  actual override suspend fun readHexadecimalUnsignedLong(): Long = commonReadHexadecimalUnsignedLong()

  actual override suspend fun readByteString(): ByteString = commonReadByteString()

  @Throws(EOFException::class)
  actual override suspend fun readByteString(byteCount: Long) = commonReadByteString(byteCount)

  actual override suspend fun select(options: Options): Int = commonSelect(options)

  actual override suspend fun <T : Any> select(options: TypedOptions<T>): T? = commonSelect(options)

  @Throws(EOFException::class)
  actual override suspend fun readFully(sink: Buffer, byteCount: Long): Unit = commonReadFully(sink, byteCount)

  @Throws(IOException::class)
  actual override suspend fun readAll(sink: Sink): Long = commonReadAll(sink)

  actual override suspend fun readUtf8() = readString(size, Charsets.UTF_8)

  @Throws(EOFException::class)
  actual override suspend fun readUtf8(byteCount: Long) = readString(byteCount, Charsets.UTF_8)

  override suspend fun readString(charset: Charset) = readString(size, charset)

  @Throws(EOFException::class)
  override suspend fun readString(byteCount: Long, charset: Charset): String {
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
  actual override suspend fun readUtf8Line(): String? = commonReadUtf8Line()

  @Throws(EOFException::class)
  actual override suspend fun readUtf8LineStrict() = readUtf8LineStrict(Long.MAX_VALUE)

  @Throws(EOFException::class)
  actual override suspend fun readUtf8LineStrict(limit: Long): String = commonReadUtf8LineStrict(limit)

  @Throws(EOFException::class)
  actual override suspend fun readUtf8CodePoint(): Int = commonReadUtf8CodePoint()

  actual override suspend fun readByteArray() = commonReadByteArray()

  @Throws(EOFException::class)
  actual override suspend fun readByteArray(byteCount: Long): ByteArray = commonReadByteArray(byteCount)

  actual override suspend fun read(sink: ByteArray) = commonRead(sink)

  @Throws(EOFException::class)
  actual override suspend fun readFully(sink: ByteArray) = commonReadFully(sink)

  actual override suspend fun read(sink: ByteArray, offset: Int, byteCount: Int): Int =
    commonRead(sink, offset, byteCount)

  actual suspend fun clear() = commonClear()

  @Throws(EOFException::class)
  actual override suspend fun skip(byteCount: Long) = commonSkip(byteCount)

  actual override suspend fun write(byteString: ByteString): Buffer = commonWrite(byteString)

  actual override suspend fun write(byteString: ByteString, offset: Int, byteCount: Int) =
    commonWrite(byteString, offset, byteCount)

  actual override suspend fun writeUtf8(string: String): Buffer = writeUtf8(string, 0, string.length)

  actual override suspend fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): Buffer =
    commonWriteUtf8(string, beginIndex, endIndex)

  actual override suspend fun writeUtf8CodePoint(codePoint: Int): Buffer =
    commonWriteUtf8CodePoint(codePoint)

  override suspend fun writeString(string: String, charset: Charset) = writeString(
    string,
    0,
    string.length,
    charset,
  )

  override suspend fun writeString(
    string: String,
    beginIndex: Int,
    endIndex: Int,
    charset: Charset,
  ): Buffer {
    require(beginIndex >= 0) { "beginIndex < 0: $beginIndex" }
    require(endIndex >= beginIndex) { "endIndex < beginIndex: $endIndex < $beginIndex" }
    require(endIndex <= string.length) { "endIndex > string.length: $endIndex > ${string.length}" }
    if (charset == Charsets.UTF_8) return writeUtf8(string, beginIndex, endIndex)
    val data = string.substring(beginIndex, endIndex).toByteArray(charset)
    return write(data, 0, data.size)
  }

  actual override suspend fun write(source: ByteArray): Buffer = commonWrite(source)

  actual override suspend fun write(
    source: ByteArray,
    offset: Int,
    byteCount: Int,
  ): Buffer = commonWrite(source, offset, byteCount)

  @Throws(IOException::class)
  actual override suspend fun writeAll(source: Source): Long = commonWriteAll(source)

  @Throws(IOException::class)
  actual override suspend fun write(source: Source, byteCount: Long): Buffer =
    commonWrite(source, byteCount)

  actual override suspend fun writeByte(b: Int): Buffer = commonWriteByte(b)

  actual override suspend fun writeShort(s: Int): Buffer = commonWriteShort(s)

  actual override suspend fun writeShortLe(s: Int) = writeShort(s.toShort().reverseBytes().toInt())

  actual override suspend fun writeInt(i: Int): Buffer = commonWriteInt(i)

  actual override suspend fun writeIntLe(i: Int) = writeInt(i.reverseBytes())

  actual override suspend fun writeLong(v: Long): Buffer = commonWriteLong(v)

  actual override suspend fun writeLongLe(v: Long) = writeLong(v.reverseBytes())

  actual override suspend fun writeDecimalLong(v: Long): Buffer = commonWriteDecimalLong(v)

  actual override suspend fun writeHexadecimalUnsignedLong(v: Long): Buffer =
    commonWriteHexadecimalUnsignedLong(v)

  internal actual fun writableSegment(minimumCapacity: Int): Segment =
    commonWritableSegment(minimumCapacity)

  actual override suspend fun write(source: Buffer, byteCount: Long): Unit = commonWrite(source, byteCount)

  actual override suspend fun read(sink: Buffer, byteCount: Long): Long = commonRead(sink, byteCount)

  actual override suspend fun indexOf(b: Byte) = indexOf(b, 0, Long.MAX_VALUE)

  /**
   * Returns the index of `b` in this at or beyond `fromIndex`, or -1 if this buffer does not
   * contain `b` in that range.
   */
  actual override suspend fun indexOf(b: Byte, fromIndex: Long) = indexOf(b, fromIndex, Long.MAX_VALUE)

  actual override suspend fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long =
    commonIndexOf(b, fromIndex = fromIndex, toIndex = toIndex)

  @Throws(IOException::class)
  actual override suspend fun indexOf(bytes: ByteString): Long = indexOf(bytes, 0)

  @Throws(IOException::class)
  actual override suspend fun indexOf(bytes: ByteString, fromIndex: Long): Long =
    indexOf(bytes, fromIndex, Long.MAX_VALUE)

  @Throws(IOException::class)
  actual override suspend fun indexOf(bytes: ByteString, fromIndex: Long, toIndex: Long): Long =
    commonIndexOf(bytes, fromIndex = fromIndex, toIndex = toIndex)

  actual override suspend fun indexOfElement(targetBytes: ByteString) = indexOfElement(targetBytes, 0L)

  actual override suspend fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long =
    commonIndexOfElement(targetBytes, fromIndex)

  actual override suspend fun rangeEquals(offset: Long, bytes: ByteString) =
    rangeEquals(offset, bytes, 0, bytes.size)

  actual override suspend fun rangeEquals(
    offset: Long,
    bytes: ByteString,
    bytesOffset: Int,
    byteCount: Int,
  ): Boolean = commonRangeEquals(offset, bytes, bytesOffset, byteCount)

  actual override suspend fun flush() {}

  actual override suspend fun close() {}

  actual override fun timeout() = Timeout.NONE

  /**
   * Returns the 128-bit MD5 hash of this buffer.
   *
   * MD5 has been vulnerable to collisions since 2004. It should not be used in new code.
   */
  actual fun md5() = digest("MD5")

  /**
   * Returns the 160-bit SHA-1 hash of this buffer.
   *
   * SHA-1 has been vulnerable to collisions since 2017. It should not be used in new code.
   */
  actual fun sha1() = digest("SHA-1")

  /** Returns the 256-bit SHA-256 hash of this buffer. */
  actual fun sha256() = digest("SHA-256")

  /** Returns the 512-bit SHA-512 hash of this buffer. */
  actual fun sha512() = digest("SHA-512")

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

  /** Returns the 160-bit SHA-1 HMAC of this buffer. */
  actual fun hmacSha1(key: ByteString) = hmac("HmacSHA1", key)

  /** Returns the 256-bit SHA-256 HMAC of this buffer. */
  actual fun hmacSha256(key: ByteString) = hmac("HmacSHA256", key)

  /** Returns the 512-bit SHA-512 HMAC of this buffer. */
  actual fun hmacSha512(key: ByteString) = hmac("HmacSHA512", key)

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

  /**
   * Returns a deep copy of this buffer. This is the same as [copy] but allows [Buffer] to implement
   * the [Cloneable] interface on the JVM.
   */
  public override fun clone(): Buffer = copy()

  actual fun snapshot(): ByteString = commonSnapshot()

  actual fun snapshot(byteCount: Int): ByteString = commonSnapshot(byteCount)

  @JvmOverloads
  actual fun readUnsafe(unsafeCursor: UnsafeCursor): UnsafeCursor = commonReadUnsafe(unsafeCursor)

  @JvmOverloads
  actual fun readAndWriteUnsafe(unsafeCursor: UnsafeCursor): UnsafeCursor =
    commonReadAndWriteUnsafe(unsafeCursor)

  @JvmName("-deprecated_getByte")
  @Deprecated(
    message = "moved to operator function",
    replaceWith = ReplaceWith(expression = "this[index]"),
    level = DeprecationLevel.ERROR,
  )
  fun getByte(index: Long) = this[index]

  @JvmName("-deprecated_size")
  @Deprecated(
    message = "moved to val",
    replaceWith = ReplaceWith(expression = "size"),
    level = DeprecationLevel.ERROR,
  )
  fun size() = size

  actual class UnsafeCursor : cursedokio.Closeable {
    @JvmField actual var buffer: Buffer? = null

    @JvmField actual var readWrite: Boolean = false

    internal actual var segment: Segment? = null

    @JvmField actual var offset = -1L

    @JvmField actual var data: ByteArray? = null

    @JvmField actual var start = -1

    @JvmField actual var end = -1

    actual fun next(): Int = commonNext()

    actual fun seek(offset: Long): Int = commonSeek(offset)

    actual fun resizeBuffer(newSize: Long): Long = commonResizeBuffer(newSize)

    actual fun expandBuffer(minByteCount: Int): Long = commonExpandBuffer(minByteCount)

    actual override suspend fun close() {
      commonClose()
    }
  }
}
