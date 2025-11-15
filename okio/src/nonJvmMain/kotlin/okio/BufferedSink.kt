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

actual sealed interface BufferedSink : Sink {
  actual val buffer: Buffer

  @IgnorableReturnValue
  actual fun write(byteString: ByteString): BufferedSink

  @IgnorableReturnValue
  actual fun write(byteString: ByteString, offset: Int, byteCount: Int): BufferedSink

  @IgnorableReturnValue
  actual fun write(source: ByteArray): BufferedSink

  @IgnorableReturnValue
  actual fun write(source: ByteArray, offset: Int, byteCount: Int): BufferedSink

  @IgnorableReturnValue
  actual fun writeAll(source: Source): Long

  @IgnorableReturnValue
  actual fun write(source: Source, byteCount: Long): BufferedSink

  @IgnorableReturnValue
  actual fun writeUtf8(string: String): BufferedSink

  @IgnorableReturnValue
  actual fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): BufferedSink

  @IgnorableReturnValue
  actual fun writeUtf8CodePoint(codePoint: Int): BufferedSink

  @IgnorableReturnValue
  actual fun writeByte(b: Int): BufferedSink

  @IgnorableReturnValue
  actual fun writeShort(s: Int): BufferedSink

  @IgnorableReturnValue
  actual fun writeShortLe(s: Int): BufferedSink

  @IgnorableReturnValue
  actual fun writeInt(i: Int): BufferedSink

  @IgnorableReturnValue
  actual fun writeIntLe(i: Int): BufferedSink

  @IgnorableReturnValue
  actual fun writeLong(v: Long): BufferedSink

  @IgnorableReturnValue
  actual fun writeLongLe(v: Long): BufferedSink

  @IgnorableReturnValue
  actual fun writeDecimalLong(v: Long): BufferedSink

  @IgnorableReturnValue
  actual fun writeHexadecimalUnsignedLong(v: Long): BufferedSink

  @IgnorableReturnValue
  actual fun emit(): BufferedSink

  @IgnorableReturnValue
  actual fun emitCompleteSegments(): BufferedSink
}
