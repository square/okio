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

expect class Buffer() : BufferedSource, BufferedSink {
  internal var head: Segment?

  var size: Long
    internal set

  override val buffer: Buffer

  override fun emitCompleteSegments(): Buffer

  override fun emit(): Buffer

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

  override fun write(byteString: ByteString): Buffer

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
}
