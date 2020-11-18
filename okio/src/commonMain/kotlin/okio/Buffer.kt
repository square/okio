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

import kotlin.jvm.JvmField

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

  fun md5(): ByteString

  fun sha1(): ByteString

  fun sha256(): ByteString

  fun sha512(): ByteString

  /** Returns the 160-bit SHA-1 HMAC of this buffer.  */
  fun hmacSha1(key: ByteString): ByteString

  /** Returns the 256-bit SHA-256 HMAC of this buffer.  */
  fun hmacSha256(key: ByteString): ByteString

  /** Returns the 512-bit SHA-512 HMAC of this buffer.  */
  fun hmacSha512(key: ByteString): ByteString

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

  fun readUnsafe(unsafeCursor: UnsafeCursor = UnsafeCursor()): UnsafeCursor

  fun readAndWriteUnsafe(unsafeCursor: UnsafeCursor = UnsafeCursor()): UnsafeCursor

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
   * Call [UnsafeCursor.next] to advance the cursor to the next segment. This returns -1 if there
   * are no further segments in the buffer.
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
   * Change the capacity of a buffer with [resizeBuffer]. This is only permitted for read+write
   * cursors. The buffer's size always changes from the end: shrinking it removes bytes from the
   * end; growing it adds capacity to the end.
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
  class UnsafeCursor constructor() {
    @JvmField var buffer: Buffer?
    @JvmField var readWrite: Boolean

    internal var segment: Segment?
    @JvmField var offset: Long
    @JvmField var data: ByteArray?
    @JvmField var start: Int
    @JvmField var end: Int

    /**
     * Seeks to the next range of bytes, advancing the offset by `end - start`. Returns the size of
     * the readable range (at least 1), or -1 if we have reached the end of the buffer and there are
     * no more bytes to read.
     */
    fun next(): Int

    /**
     * Reposition the cursor so that the data at [offset] is readable at `data[start]`.
     * Returns the number of bytes readable in [data] (at least 1), or -1 if there are no data
     * to read.
     */
    fun seek(offset: Long): Int

    /**
     * Change the size of the buffer so that it equals [newSize] by either adding new capacity at
     * the end or truncating the buffer at the end. Newly added capacity may span multiple segments.
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
    fun resizeBuffer(newSize: Long): Long

    /**
     * Grow the buffer by adding a **contiguous range** of capacity in a single segment. This adds
     * at least [minByteCount] bytes but may add up to a full segment of additional capacity.
     *
     * As a side-effect this cursor will [seek][UnsafeCursor.seek]. It will move
     * [offset][UnsafeCursor.offset] to the first byte of newly-added capacity. This is the size of
     * the buffer prior to the `expandBuffer()` call.
     *
     * If [minByteCount] bytes are available in the buffer's current tail segment that will be used;
     * otherwise another segment will be allocated and appended. In either case this returns the
     * number of bytes of capacity added to this buffer.
     *
     * Warning: it is the caller’s responsibility to either write new data to every byte of the
     * newly-allocated capacity, or to [shrink][UnsafeCursor.resizeBuffer] the buffer to the data
     * written. Failure to do so may cause serious security problems as the data in the returned
     * buffers is not zero filled. Buffers may contain dirty pooled segments that hold very
     * sensitive data from other parts of the current process.
     *
     * @param minByteCount the size of the contiguous capacity. Must be positive and not greater
     *     than the capacity size of a single segment (8 KiB).
     * @return the number of bytes expanded by. Not less than `minByteCount`.
     */
    fun expandBuffer(minByteCount: Int): Long

    fun close()
  }
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
