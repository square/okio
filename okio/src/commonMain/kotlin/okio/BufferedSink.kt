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

expect interface BufferedSink : Sink {
  val buffer: Buffer

  fun write(byteString: ByteString): BufferedSink

  fun write(source: ByteArray): BufferedSink

  fun write(source: ByteArray, offset: Int, byteCount: Int): BufferedSink

  fun writeAll(source: Source): Long

  fun write(source: Source, byteCount: Long): BufferedSink

  fun writeUtf8(string: String): BufferedSink

  fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): BufferedSink

  fun writeUtf8CodePoint(codePoint: Int): BufferedSink

  fun writeByte(b: Int): BufferedSink

  fun writeShort(s: Int): BufferedSink

  fun writeShortLe(s: Int): BufferedSink

  fun writeInt(i: Int): BufferedSink

  fun writeIntLe(i: Int): BufferedSink

  fun writeLong(v: Long): BufferedSink

  fun writeLongLe(v: Long): BufferedSink

  fun writeDecimalLong(v: Long): BufferedSink

  fun writeHexadecimalUnsignedLong(v: Long): BufferedSink

  fun emit(): BufferedSink

  fun emitCompleteSegments(): BufferedSink
}
