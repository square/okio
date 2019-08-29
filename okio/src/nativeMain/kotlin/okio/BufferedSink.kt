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

actual interface BufferedSink : Sink {
  actual val buffer: Buffer

  actual fun write(byteString: ByteString): BufferedSink

  actual fun write(byteString: ByteString, offset: Int, byteCount: Int): BufferedSink

  actual fun write(source: ByteArray): BufferedSink

  actual fun write(source: ByteArray, offset: Int, byteCount: Int): BufferedSink

  actual fun writeAll(source: Source): Long

  actual fun write(source: Source, byteCount: Long): BufferedSink

  actual fun writeUtf8(string: String): BufferedSink

  actual fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): BufferedSink

  actual fun writeUtf8CodePoint(codePoint: Int): BufferedSink

  actual fun writeByte(b: Int): BufferedSink

  actual fun writeShort(s: Int): BufferedSink

  actual fun writeShortLe(s: Int): BufferedSink

  actual fun writeInt(i: Int): BufferedSink

  actual fun writeIntLe(i: Int): BufferedSink

  actual fun writeLong(v: Long): BufferedSink

  actual fun writeLongLe(v: Long): BufferedSink

  actual fun writeDecimalLong(v: Long): BufferedSink

  actual fun writeHexadecimalUnsignedLong(v: Long): BufferedSink

  actual fun emit(): BufferedSink

  actual fun emitCompleteSegments(): BufferedSink
}
