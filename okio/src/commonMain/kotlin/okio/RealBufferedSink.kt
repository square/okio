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

@file:MustUseReturnValue
package okio

internal expect class RealBufferedSink(
  sink: Sink,
) : BufferedSink {
  val sink: Sink
  var closed: Boolean

  override val buffer: Buffer
  override fun close()
  @IgnorableReturnValue
  override fun emit(): BufferedSink
  @IgnorableReturnValue
  override fun emitCompleteSegments(): BufferedSink
  override fun flush()
  override fun timeout(): Timeout
  @IgnorableReturnValue
  override fun write(byteString: ByteString): BufferedSink
  @IgnorableReturnValue
  override fun write(byteString: ByteString, offset: Int, byteCount: Int): BufferedSink
  override fun write(source: Buffer, byteCount: Long)
  @IgnorableReturnValue
  override fun write(source: ByteArray): BufferedSink
  @IgnorableReturnValue
  override fun write(source: ByteArray, offset: Int, byteCount: Int): BufferedSink
  @IgnorableReturnValue
  override fun write(source: Source, byteCount: Long): BufferedSink
  @IgnorableReturnValue
  override fun writeAll(source: Source): Long
  @IgnorableReturnValue
  override fun writeByte(b: Int): BufferedSink
  @IgnorableReturnValue
  override fun writeDecimalLong(v: Long): BufferedSink
  @IgnorableReturnValue
  override fun writeHexadecimalUnsignedLong(v: Long): BufferedSink
  @IgnorableReturnValue
  override fun writeInt(i: Int): BufferedSink
  @IgnorableReturnValue
  override fun writeIntLe(i: Int): BufferedSink
  @IgnorableReturnValue
  override fun writeLong(v: Long): BufferedSink
  @IgnorableReturnValue
  override fun writeLongLe(v: Long): BufferedSink
  @IgnorableReturnValue
  override fun writeShort(s: Int): BufferedSink
  @IgnorableReturnValue
  override fun writeShortLe(s: Int): BufferedSink
  @IgnorableReturnValue
  override fun writeUtf8(string: String): BufferedSink
  @IgnorableReturnValue
  override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): BufferedSink
  @IgnorableReturnValue
  override fun writeUtf8CodePoint(codePoint: Int): BufferedSink
}
