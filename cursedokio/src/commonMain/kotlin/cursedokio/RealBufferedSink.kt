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

package cursedokio

internal expect class RealBufferedSink(
  sink: Sink,
) : BufferedSink {
  val sink: Sink
  var closed: Boolean

  override val buffer: Buffer
  override suspend fun close()
  override suspend fun emit(): BufferedSink
  override suspend fun emitCompleteSegments(): BufferedSink
  override suspend fun flush()
  override fun timeout(): Timeout
  override suspend fun write(byteString: ByteString): BufferedSink
  override suspend fun write(byteString: ByteString, offset: Int, byteCount: Int): BufferedSink
  override suspend fun write(source: Buffer, byteCount: Long)
  override suspend fun write(source: ByteArray): BufferedSink
  override suspend fun write(source: ByteArray, offset: Int, byteCount: Int): BufferedSink
  override suspend fun write(source: Source, byteCount: Long): BufferedSink
  override suspend fun writeAll(source: Source): Long
  override suspend fun writeByte(b: Int): BufferedSink
  override suspend fun writeDecimalLong(v: Long): BufferedSink
  override suspend fun writeHexadecimalUnsignedLong(v: Long): BufferedSink
  override suspend fun writeInt(i: Int): BufferedSink
  override suspend fun writeIntLe(i: Int): BufferedSink
  override suspend fun writeLong(v: Long): BufferedSink
  override suspend fun writeLongLe(v: Long): BufferedSink
  override suspend fun writeShort(s: Int): BufferedSink
  override suspend fun writeShortLe(s: Int): BufferedSink
  override suspend fun writeUtf8(string: String): BufferedSink
  override suspend fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): BufferedSink
  override suspend fun writeUtf8CodePoint(codePoint: Int): BufferedSink
}
