/*
 * Copyright (C) 2024 Square, Inc.
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

private val emptyByteArray = byteArrayOf()

/**
 * Transform a stream of source bytes into a stream of target bytes, one segment at a time. The
 * relationship between input byte count and output byte count is arbitrary: a sequence of input
 * bytes may produce zero output bytes, or many segments of output bytes.
 *
 * To use:
 *
 *  1. Create an instance.
 *
 *  2. Populate [source] with input data. Set [sourcePos] and [sourceLimit] to a readable slice of
 *     this array.
 *
 *  3. Populate [target] with a destination for output data. Set [targetPos] and [targetLimit] to a
 *     writable slice of this array.
 *
 *  4. Call [process] to read input data from [source] and write output to [target]. This function
 *     advances [sourcePos] if input data was read and [targetPos] if compressed output was written.
 *     If the input array is exhausted (`sourcePos == sourceLimit`) or the output array is full
 *     (`targetPos == targetLimit`), make an adjustment and call [process] again.
 *
 *  5. Repeat steps 2 through 4 until the input data is completely exhausted.
 *
 *  6. Close the processor.
 *
 * See also, the [zlib manual](https://www.zlib.net/manual.html).
 */
internal abstract class DataProcessor : Closeable {
  var source: ByteArray = emptyByteArray
  var sourcePos: Int = 0
  var sourceLimit: Int = 0

  var target: ByteArray = emptyByteArray
  var targetPos: Int = 0
  var targetLimit: Int = 0

  var closed: Boolean = false
    protected set

  /** True if the content is self-terminating and has reached the end of the stream. */
  var finished: Boolean = false
    internal set

  /**
   * Returns true if no further calls to [process] are required to complete the operation.
   * Otherwise, make space available in [target] and call this again.
   */
  @Throws(ProtocolException::class)
  abstract fun process(): Boolean

  /** True if calling [process] may produce more output without more input. */
  private var callProcess = false

  /**
   * Consume [sourceExactByteCount] bytes from [source], writing any amount of output to [target].
   *
   * Note that 0 is a valid number of bytes to process, and this will cause flush and finish blocks
   * to be written. For such 0-byte writes [source] may be null.
   */
  @Throws(IOException::class)
  fun writeBytesFromSource(
    source: Buffer?,
    sourceExactByteCount: Long,
    target: BufferedSink,
  ) {
    check(!closed) { "closed" }

    var byteCount = 0
    while (true) {
      val sourceHead = prepareSource(source, sourceExactByteCount - byteCount)
      val targetTail = prepareTarget(target)
      try {
        callProcess = !process()
      } finally {
        byteCount += updateSource(source, sourceHead)
        updateTarget(target, targetTail)
      }

      // If we've produced a full segment, emit it. This blocks writing to the target.
      target.emitCompleteSegments()

      // Keep going until we've consumed the required byte count.
      if (byteCount < sourceExactByteCount) continue

      // More output is available without consuming more input. Produce it.
      if (callProcess) continue

      break
    }
  }

  /**
   * Produce up to [targetMaxByteCount] bytes to target, reading any number of bytes from [source].
   *
   * @return the total number of bytes produced to [target], or -1L if no bytes were produced.
   */
  @Throws(IOException::class)
  fun readBytesToTarget(
    source: BufferedSource,
    targetMaxByteCount: Long,
    target: Buffer,
  ): Long {
    check(!closed) { "closed" }

    var byteCount = 0L
    while (true) {
      // Make sure we have input to process. This blocks reading the source.
      val sourceExhausted = when {
        !callProcess && byteCount == 0L -> finished || source.exhausted()
        else -> false
      }

      val sourceHead = prepareSource(source.buffer)
      val targetTail = prepareTarget(target, targetMaxByteCount - byteCount)
      try {
        callProcess = !process()
      } finally {
        updateSource(source.buffer, sourceHead)
        byteCount += updateTarget(target, targetTail)
      }

      // Keep going until either we produce 1+ byte of output, or we exhaust the stream.
      if (!sourceExhausted && byteCount == 0L) continue

      // More output is available without consuming more input. Produce it.
      if (callProcess && byteCount < targetMaxByteCount) continue

      break
    }

    return when {
      byteCount > 0L -> byteCount
      else -> -1L
    }
  }

  /** Tell the processor to read up to [maxByteCount] bytes from the source's first segment. */
  private fun prepareSource(
    source: Buffer?,
    maxByteCount: Long = Long.MAX_VALUE,
  ): Segment? {
    val head = source?.buffer?.head
    if (maxByteCount == 0L || head == null) {
      sourcePos = 0
      sourceLimit = 0
      return null
    }

    val toProcess = minOf(maxByteCount, head.limit - head.pos).toInt()
    this.source = head.data
    this.sourcePos = head.pos
    this.sourceLimit = head.pos + toProcess
    return head
  }

  /**
   * Track what was consumed from the source, if anything.
   *
   * Returns the number of consumed bytes.
   */
  private fun updateSource(
    source: Buffer?,
    sourceHead: Segment?,
  ): Int {
    if (sourceLimit == 0) return 0

    source!!
    val consumedByteCount = sourcePos - sourceHead!!.pos
    sourceHead.pos = sourcePos
    source.size -= consumedByteCount

    // If we used up the head segment, recycle it.
    if (sourceHead.pos == sourceHead.limit) {
      source.head = sourceHead.pop()
      SegmentPool.recycle(sourceHead)
    }

    this.source = emptyByteArray
    this.sourcePos = 0
    this.sourceLimit = 0

    return consumedByteCount
  }

  /** Tell the processor to write to the target's last segment. */
  private fun prepareTarget(
    target: BufferedSink,
    maxByteCount: Long = Long.MAX_VALUE,
  ): Segment {
    val tail = target.buffer.writableSegment(1)
    val toProcess = minOf(maxByteCount, tail.data.size - tail.limit).toInt()
    this.target = tail.data
    this.targetPos = tail.limit
    this.targetLimit = tail.limit + toProcess
    return tail
  }

  /**
   * Track what was produced on the target, if anything, and emit the bytes to the target stream.
   *
   * Returns the number of produced bytes.
   */
  private fun updateTarget(
    target: BufferedSink,
    tail: Segment,
  ): Int {
    val producedByteCount = targetPos - tail.limit

    if (producedByteCount == 0 && tail.pos == tail.limit) {
      // We allocated a tail segment, but didn't end up needing it. Recycle!
      target.buffer.head = tail.pop()
      SegmentPool.recycle(tail)
    } else {
      tail.limit = targetPos
      target.buffer.size += producedByteCount
    }

    this.target = emptyByteArray
    this.targetPos = 0
    this.targetLimit = 0

    return producedByteCount
  }
}
