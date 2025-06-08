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

internal expect class RealBufferedSource(
  source: Source,
) : BufferedSource {
  val source: Source
  var closed: Boolean

  override val buffer: Buffer
  override suspend fun close()
  override suspend fun exhausted(): Boolean
  override suspend fun indexOf(b: Byte): Long
  override suspend fun indexOf(b: Byte, fromIndex: Long): Long
  override suspend fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long
  override suspend fun indexOf(bytes: ByteString): Long
  override suspend fun indexOf(bytes: ByteString, fromIndex: Long): Long
  override suspend fun indexOf(bytes: ByteString, fromIndex: Long, toIndex: Long): Long
  override suspend fun indexOfElement(targetBytes: ByteString): Long
  override suspend fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long
  override fun peek(): BufferedSource
  override suspend fun rangeEquals(offset: Long, bytes: ByteString): Boolean
  override suspend fun rangeEquals(offset: Long, bytes: ByteString, bytesOffset: Int, byteCount: Int): Boolean
  override suspend fun read(sink: Buffer, byteCount: Long): Long
  override suspend fun read(sink: ByteArray): Int
  override suspend fun read(sink: ByteArray, offset: Int, byteCount: Int): Int
  override suspend fun readAll(sink: Sink): Long
  override suspend fun readByte(): Byte
  override suspend fun readByteArray(): ByteArray
  override suspend fun readByteArray(byteCount: Long): ByteArray
  override suspend fun readByteString(): ByteString
  override suspend fun readByteString(byteCount: Long): ByteString
  override suspend fun readDecimalLong(): Long
  override suspend fun readFully(sink: Buffer, byteCount: Long)
  override suspend fun readFully(sink: ByteArray)
  override suspend fun readHexadecimalUnsignedLong(): Long
  override suspend fun readInt(): Int
  override suspend fun readIntLe(): Int
  override suspend fun readLong(): Long
  override suspend fun readLongLe(): Long
  override suspend fun readShort(): Short
  override suspend fun readShortLe(): Short
  override suspend fun readUtf8(): String
  override suspend fun readUtf8(byteCount: Long): String
  override suspend fun readUtf8CodePoint(): Int
  override suspend fun readUtf8Line(): String?
  override suspend fun readUtf8LineStrict(): String
  override suspend fun readUtf8LineStrict(limit: Long): String
  override suspend fun request(byteCount: Long): Boolean
  override suspend fun require(byteCount: Long)
  override suspend fun select(options: Options): Int
  override suspend fun <T : Any> select(options: TypedOptions<T>): T?
  override suspend fun skip(byteCount: Long)
  override fun timeout(): Timeout
}
