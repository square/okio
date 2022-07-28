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

actual sealed interface Sink : RawSink {
  actual val buffer: Buffer

  actual fun write(byteString: ByteString): Sink

  actual fun write(byteString: ByteString, offset: Int, byteCount: Int): Sink

  actual fun write(source: ByteArray): Sink

  actual fun write(source: ByteArray, offset: Int, byteCount: Int): Sink

  actual fun writeAll(source: RawSource): Long

  actual fun write(source: RawSource, byteCount: Long): Sink

  actual fun writeUtf8(string: String): Sink

  actual fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): Sink

  actual fun writeUtf8CodePoint(codePoint: Int): Sink

  actual fun writeByte(b: Int): Sink

  actual fun writeShort(s: Int): Sink

  actual fun writeShortLe(s: Int): Sink

  actual fun writeInt(i: Int): Sink

  actual fun writeIntLe(i: Int): Sink

  actual fun writeLong(v: Long): Sink

  actual fun writeLongLe(v: Long): Sink

  actual fun writeDecimalLong(v: Long): Sink

  actual fun writeHexadecimalUnsignedLong(v: Long): Sink

  actual fun emit(): Sink

  actual fun emitCompleteSegments(): Sink
}
