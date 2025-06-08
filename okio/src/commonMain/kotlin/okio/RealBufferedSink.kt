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

internal expect class RealBufferedSink(
  sink: Sink,
) : BufferedSink {
  val sink: Sink
  var closed: Boolean

  override val buffer: Buffer
  override fun close()
  override fun emit(): BufferedSink
  override fun emitCompleteSegments(): BufferedSink
  override fun flush()
  override fun timeout(): Timeout
  override fun write(byteString: ByteString): BufferedSink
  override fun write(byteString: ByteString, offset: Int, byteCount: Int): BufferedSink
  override fun write(source: Buffer, byteCount: Long)
  override fun write(source: ByteArray): BufferedSink
  override fun write(source: ByteArray, offset: Int, byteCount: Int): BufferedSink
  override fun write(source: Source, byteCount: Long): BufferedSink
  override fun writeAll(source: Source): Long
  override fun writeByte(b: Int): BufferedSink
  override fun writeDecimalLong(v: Long): BufferedSink
  override fun writeHexadecimalUnsignedLong(v: Long): BufferedSink
  override fun writeInt(i: Int): BufferedSink
  override fun writeIntLe(i: Int): BufferedSink
  override fun writeLong(v: Long): BufferedSink
  override fun writeLongLe(v: Long): BufferedSink
  override fun writeShort(s: Int): BufferedSink
  override fun writeShortLe(s: Int): BufferedSink
  override fun writeUtf8(string: String): BufferedSink
  override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): BufferedSink
  override fun writeUtf8CodePoint(codePoint: Int): BufferedSink
}
