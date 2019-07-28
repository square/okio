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

import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.decodeHex
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class BufferSinkTest : AbstractBufferedSinkTest(BufferedSinkFactory.BUFFER)
class RealBufferedSinkTest : AbstractBufferedSinkTest(BufferedSinkFactory.REAL_BUFFERED_SINK)

abstract class AbstractBufferedSinkTest internal constructor(
  factory: BufferedSinkFactory
) {
  private val data: Buffer = Buffer()
  private val sink: BufferedSink = factory.create(data)

  @Test fun writeNothing() {
    sink.writeUtf8("")
    sink.flush()
    assertEquals(0, data.size)
  }

  @Test fun writeBytes() {
    sink.writeByte(0xab)
    sink.writeByte(0xcd)
    sink.flush()
    assertEquals("[hex=abcd]", data.toString())
  }

  @Test fun writeLastByteInSegment() {
    sink.writeUtf8("a".repeat(Segment.SIZE - 1))
    sink.writeByte(0x20)
    sink.writeByte(0x21)
    sink.flush()
    assertEquals(listOf(Segment.SIZE, 1), segmentSizes(data))
    assertEquals("a".repeat(Segment.SIZE - 1), data.readUtf8(Segment.SIZE - 1L))
    assertEquals("[text= !]", data.toString())
  }

  @Test fun writeShort() {
    sink.writeShort(0xabcd)
    sink.writeShort(0x4321)
    sink.flush()
    assertEquals("[hex=abcd4321]", data.toString())
  }

  @Test fun writeShortLe() {
    sink.writeShortLe(0xcdab)
    sink.writeShortLe(0x2143)
    sink.flush()
    assertEquals("[hex=abcd4321]", data.toString())
  }

  @Test fun writeInt() {
    sink.writeInt(-0x543210ff)
    sink.writeInt(-0x789abcdf)
    sink.flush()
    assertEquals("[hex=abcdef0187654321]", data.toString())
  }

  @Test fun writeLastIntegerInSegment() {
    sink.writeUtf8("a".repeat(Segment.SIZE - 4))
    sink.writeInt(-0x543210ff)
    sink.writeInt(-0x789abcdf)
    sink.flush()
    assertEquals(listOf(Segment.SIZE, 4), segmentSizes(data))
    assertEquals("a".repeat(Segment.SIZE - 4), data.readUtf8(Segment.SIZE - 4L))
    assertEquals("[hex=abcdef0187654321]", data.toString())
  }

  @Test fun writeIntegerDoesNotQuiteFitInSegment() {
    sink.writeUtf8("a".repeat(Segment.SIZE - 3))
    sink.writeInt(-0x543210ff)
    sink.writeInt(-0x789abcdf)
    sink.flush()
    assertEquals(listOf(Segment.SIZE - 3, 8), segmentSizes(data))
    assertEquals("a".repeat(Segment.SIZE - 3), data.readUtf8(Segment.SIZE - 3L))
    assertEquals("[hex=abcdef0187654321]", data.toString())
  }

  @Test fun writeIntLe() {
    sink.writeIntLe(-0x543210ff)
    sink.writeIntLe(-0x789abcdf)
    sink.flush()
    assertEquals("[hex=01efcdab21436587]", data.toString())
  }

  @Test fun writeLong() {
    sink.writeLong(-0x543210fe789abcdfL)
    sink.writeLong(-0x350145414f4ea400L)
    sink.flush()
    assertEquals("[hex=abcdef0187654321cafebabeb0b15c00]", data.toString())
  }

  @Test fun writeLongLe() {
    sink.writeLongLe(-0x543210fe789abcdfL)
    sink.writeLongLe(-0x350145414f4ea400L)
    sink.flush()
    assertEquals("[hex=2143658701efcdab005cb1b0bebafeca]", data.toString())
  }

  @Test fun writeByteString() {
    sink.write("təˈranəˌsôr".encodeUtf8())
    sink.flush()
    assertEquals("74c999cb8872616ec999cb8c73c3b472".decodeHex(), data.readByteString())
  }

  @Test fun writeByteStringOffset() {
    sink.write("təˈranəˌsôr".encodeUtf8(), 5, 5)
    sink.flush()
    assertEquals("72616ec999".decodeHex(), data.readByteString())
  }

  @Test fun writeStringUtf8() {
    sink.writeUtf8("təˈranəˌsôr")
    sink.flush()
    assertEquals("74c999cb8872616ec999cb8c73c3b472".decodeHex(), data.readByteString())
  }

  @Test fun writeSubstringUtf8() {
    sink.writeUtf8("təˈranəˌsôr", 3, 7)
    sink.flush()
    assertEquals("72616ec999".decodeHex(), data.readByteString())
  }

  @Test fun writeAll() {
    val source = Buffer().writeUtf8("abcdef")

    assertEquals(6, sink.writeAll(source))
    assertEquals(0, source.size)
    sink.flush()
    assertEquals("abcdef", data.readUtf8())
  }

  @Test fun writeSource() {
    val source = Buffer().writeUtf8("abcdef")

    // Force resolution of the Source method overload.
    sink.write(source as Source, 4)
    sink.flush()
    assertEquals("abcd", data.readUtf8())
    assertEquals("ef", source.readUtf8())
  }

  @Test fun writeSourceReadsFully() {
    val source = object : Source by Buffer() {
      override fun read(sink: Buffer, byteCount: Long): Long {
        sink.writeUtf8("abcd")
        return 4
      }
    }

    sink.write(source, 8)
    sink.flush()
    assertEquals("abcdabcd", data.readUtf8())
  }

  @Test fun writeSourcePropagatesEof() {
    val source: Source = Buffer().writeUtf8("abcd")

    try {
      sink.write(source, 8)
      fail()
    } catch (expected: EOFException) {
    }

    // Ensure that whatever was available was correctly written.
    sink.flush()
    assertEquals("abcd", data.readUtf8())
  }

  @Test fun writeSourceWithZeroIsNoOp() {
    // This test ensures that a zero byte count never calls through to read the source. It may be
    // tied to something like a socket which will potentially block trying to read a segment when
    // ultimately we don't want any data.
    val source = object : Source by Buffer() {
      override fun read(sink: Buffer, byteCount: Long): Long {
        throw AssertionError()
      }
    }
    sink.write(source, 0)
    assertEquals(0, data.size)
  }

  @Test fun writeAllExhausted() {
    val source = Buffer()
    assertEquals(0, sink.writeAll(source))
    assertEquals(0, source.size)
  }

  @Test fun closeEmitsBufferedBytes() {
    sink.writeByte('a'.toInt())
    sink.close()
    assertEquals('a', data.readByte().toChar())
  }

  @Test fun longDecimalString() {
    assertLongDecimalString(0)
    assertLongDecimalString(Long.MIN_VALUE)
    assertLongDecimalString(Long.MAX_VALUE)

    for (i in 1..19) {
      val value = 10.0.pow(i).toLong()
      assertLongDecimalString(value - 1)
      assertLongDecimalString(value)
    }
  }

  private fun assertLongDecimalString(value: Long) {
    sink.writeDecimalLong(value).writeUtf8("zzz").flush()
    val expected = "${value}zzz"
    val actual = data.readUtf8()
    assertEquals(expected, actual, "$value expected $expected but was $actual")
  }

  @Test fun longHexString() {
    assertLongHexString(0)
    assertLongHexString(Long.MIN_VALUE)
    assertLongHexString(Long.MAX_VALUE)

    for (i in 0..62) {
      assertLongHexString((1L shl i) - 1)
      assertLongHexString(1L shl i)
    }
  }

  private fun assertLongHexString(value: Long) {
    sink.writeHexadecimalUnsignedLong(value).writeUtf8("zzz").flush()
    val expected = "${value.toHexString()}zzz"
    val actual = data.readUtf8()
    assertEquals(expected, actual, "$value expected $expected but was $actual")
  }
}
