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

  /** Copy `byteCount` bytes from this, starting at `offset`, to `out`.  */
  fun copyTo(
    out: Buffer,
    offset: Long = 0L,
    byteCount: Long,
  ): Buffer

  /**
   * Overload of [copyTo] with byteCount = size - offset, work around for
   *  https://youtrack.jetbrains.com/issue/KT-30847
   */
  fun copyTo(
    out: Buffer,
    offset: Long = 0L,
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

  /**
   * Returns a deep copy of this buffer. The returned [Buffer] initially shares the underlying
   * [ByteArray]s. See [UnsafeCursor] for more details.
   */
  fun copy(): Buffer

  /** Returns an immutable copy of this buffer as a byte string.  */
  fun snapshot(): ByteString

  /** Returns an immutable copy of the first `byteCount` bytes of this buffer as a byte string. */
  fun snapshot(byteCount: Int): ByteString

  fun readUnsafe(unsafeCursor: UnsafeCursor = DEFAULT__new_UnsafeCursor): UnsafeCursor

  fun readAndWriteUnsafe(unsafeCursor: UnsafeCursor = DEFAULT__new_UnsafeCursor): UnsafeCursor

  override val buffer: Buffer
  override fun close()
  override fun emit(): Buffer
  override fun emitCompleteSegments(): Buffer
  override fun exhausted(): Boolean
  override fun flush()
  override fun indexOf(b: Byte): Long
  override fun indexOf(b: Byte, fromIndex: Long): Long
  override fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long
  override fun indexOf(bytes: ByteString): Long
  override fun indexOf(bytes: ByteString, fromIndex: Long): Long
  override fun indexOfElement(targetBytes: ByteString): Long
  override fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long
  override fun peek(): BufferedSource
  override fun rangeEquals(offset: Long, bytes: ByteString): Boolean
  override fun rangeEquals(offset: Long, bytes: ByteString, bytesOffset: Int, byteCount: Int): Boolean
  override fun read(sink: Buffer, byteCount: Long): Long
  override fun read(sink: ByteArray): Int
  override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int
  override fun readAll(sink: Sink): Long
  override fun readByte(): Byte
  override fun readByteArray(): ByteArray
  override fun readByteArray(byteCount: Long): ByteArray
  override fun readByteString(): ByteString
  override fun readByteString(byteCount: Long): ByteString
  override fun readDecimalLong(): Long
  override fun readFully(sink: Buffer, byteCount: Long)
  override fun readFully(sink: ByteArray)
  override fun readHexadecimalUnsignedLong(): Long
  override fun readInt(): Int
  override fun readIntLe(): Int
  override fun readLong(): Long
  override fun readLongLe(): Long
  override fun readShort(): Short
  override fun readShortLe(): Short
  override fun readUtf8(): String
  override fun readUtf8(byteCount: Long): String
  override fun readUtf8CodePoint(): Int
  override fun readUtf8Line(): String?
  override fun readUtf8LineStrict(): String
  override fun readUtf8LineStrict(limit: Long): String
  override fun request(byteCount: Long): Boolean
  override fun require(byteCount: Long)
  override fun select(options: Options): Int
  override fun <T : Any> select(options: TypedOptions<T>): T?
  override fun timeout(): Timeout
  override fun write(byteString: ByteString): Buffer
  override fun write(byteString: ByteString, offset: Int, byteCount: Int): Buffer
  override fun write(source: Buffer, byteCount: Long)
  override fun write(source: ByteArray): Buffer
  override fun write(source: ByteArray, offset: Int, byteCount: Int): Buffer
  override fun write(source: Source, byteCount: Long): Buffer
  override fun writeAll(source: Source): Long
  override fun writeByte(b: Int): Buffer
  override fun writeDecimalLong(v: Long): Buffer
  override fun writeHexadecimalUnsignedLong(v: Long): Buffer
  override fun writeInt(i: Int): Buffer
  override fun writeIntLe(i: Int): Buffer
  override fun writeLong(v: Long): Buffer
  override fun writeLongLe(v: Long): Buffer
  override fun writeShort(s: Int): Buffer
  override fun writeShortLe(s: Int): Buffer
  override fun writeUtf8(string: String): Buffer
  override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): Buffer
  override fun writeUtf8CodePoint(codePoint: Int): Buffer

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
   * ```kotlin
   *   val buffer = Buffer()
   * ```
   *
   * We append 7 bytes of data to the end of our empty buffer. Internally, the buffer allocates a
   * segment and writes its new data there. The lone segment has an 8 KiB byte array but only 7
   * bytes of data:
   *
   * ```kotlin
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
   * ```kotlin
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
   * ```kotlin
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
   * ```kotlin
   * val abc = new Buffer()
   * abc.writeUtf8("abc")
   *
   * // [ 'a', 'b', 'c', 'o', 'x', 'o', 'x', 'o', ...]
   * //    ^              ^
   * // start = 0     end = 3
   * ```
   *
   * There is an optimization in `Buffer.copy()` and other methods that allows two segments to
   * share the same underlying byte array. Clones can't write to the shared byte array; instead they
   * allocate a new (private) segment early.
   *
   * ```kotlin
   * val nana = new Buffer()
   * nana.writeUtf8("na".repeat(2_500))
   * nana.readUtf8(2) // "na"
   *
   * // [ 'n', 'a', 'n', 'a', ..., 'n', 'a', 'n', 'a', '?', '?', '?', ...]
   * //              ^                                  ^
   * //           start = 2                         end = 5000
   *
   * nana2 = nana.copy()
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
   * ```kotlin
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
  class UnsafeCursor constructor() : Closeable {
    var buffer: Buffer?

    var readWrite: Boolean

    internal var segment: Segment?

    var offset: Long

    var data: ByteArray?

    var start: Int

    var end: Int

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

    override fun close()
  }
}
