/*
 * Copyright (C) 2014 Square, Inc.
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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.of
import okio.internal.CRC32

class GzipSourceTest {
  @Test
  fun gunzip() {
    val gzipped = Buffer()
    gzipped.write(gzipHeader)
    gzipped.write(deflated)
    gzipped.write(gzipTrailer)
    assertGzipped(gzipped)
  }

  @Test
  fun gunzip_withHCRC() {
    val hcrc = CRC32()
    val gzipHeader = gzipHeaderWithFlags(0x02.toByte())
    hcrc.update(gzipHeader.toByteArray())
    val gzipped = Buffer()
    gzipped.write(gzipHeader)
    gzipped.writeShort(hcrc.getValue().toShort().reverseBytes().toInt()) // little endian
    gzipped.write(deflated)
    gzipped.write(gzipTrailer)
    assertGzipped(gzipped)
  }

  @Test
  fun gunzip_withExtra() {
    val gzipped = Buffer()
    gzipped.write(gzipHeaderWithFlags(0x04.toByte()))
    gzipped.writeShort(7.toShort().reverseBytes().toInt()) // little endian extra length
    gzipped.write("blubber".encodeUtf8().toByteArray(), 0, 7)
    gzipped.write(deflated)
    gzipped.write(gzipTrailer)
    assertGzipped(gzipped)
  }

  @Test
  fun gunzip_withName() {
    val gzipped = Buffer()
    gzipped.write(gzipHeaderWithFlags(0x08.toByte()))
    gzipped.write("foo.txt".encodeUtf8().toByteArray(), 0, 7)
    gzipped.writeByte(0) // zero-terminated
    gzipped.write(deflated)
    gzipped.write(gzipTrailer)
    assertGzipped(gzipped)
  }

  @Test
  fun gunzip_withComment() {
    val gzipped = Buffer()
    gzipped.write(gzipHeaderWithFlags(0x10.toByte()))
    gzipped.write("rubbish".encodeUtf8().toByteArray(), 0, 7)
    gzipped.writeByte(0) // zero-terminated
    gzipped.write(deflated)
    gzipped.write(gzipTrailer)
    assertGzipped(gzipped)
  }

  /**
   * For portability, it is a good idea to export the gzipped bytes and try running gzip.  Ex.
   * `echo gzipped | base64 --decode | gzip -l -v`
   */
  @Test
  fun gunzip_withAll() {
    val gzipped = Buffer()
    gzipped.write(gzipHeaderWithFlags(0x1c.toByte()))
    gzipped.writeShort(7.toShort().reverseBytes().toInt()) // little endian extra length
    gzipped.write("blubber".encodeUtf8().toByteArray(), 0, 7)
    gzipped.write("foo.txt".encodeUtf8().toByteArray(), 0, 7)
    gzipped.writeByte(0) // zero-terminated
    gzipped.write("rubbish".encodeUtf8().toByteArray(), 0, 7)
    gzipped.writeByte(0) // zero-terminated
    gzipped.write(deflated)
    gzipped.write(gzipTrailer)
    assertGzipped(gzipped)
  }

  private fun assertGzipped(gzipped: Buffer) {
    val gunzipped = gunzip(gzipped)
    assertEquals("It's a UNIX system! I know this!", gunzipped.readUtf8())
  }

  /**
   * Note that you cannot test this with old versions of gzip, as they interpret flag bit 1 as
   * CONTINUATION, not HCRC. For example, this is the case with the default gzip on osx.
   */
  @Test
  fun gunzipWhenHeaderCRCIncorrect() {
    val gzipped = Buffer()
    gzipped.write(gzipHeaderWithFlags(0x02.toByte()))
    gzipped.writeShort(0.toShort().toInt()) // wrong HCRC!
    gzipped.write(deflated)
    gzipped.write(gzipTrailer)
    try {
      gunzip(gzipped)
      fail()
    } catch (e: IOException) {
      assertEquals("FHCRC: actual 0x0000261d != expected 0x00000000", e.message)
    }
  }

  @Test
  fun gunzipWhenCRCIncorrect() {
    val gzipped = Buffer()
    gzipped.write(gzipHeader)
    gzipped.write(deflated)
    gzipped.writeInt(0x1234567.reverseBytes()) // wrong CRC
    gzipped.write(gzipTrailer.toByteArray(), 3, 4)
    try {
      gunzip(gzipped)
      fail()
    } catch (e: IOException) {
      assertEquals("CRC: actual 0x37ad8f8d != expected 0x01234567", e.message)
    }
  }

  @Test
  fun gunzipWhenLengthIncorrect() {
    val gzipped = Buffer()
    gzipped.write(gzipHeader)
    gzipped.write(deflated)
    gzipped.write(gzipTrailer.toByteArray(), 0, 4)
    gzipped.writeInt(0x123456.reverseBytes()) // wrong length
    try {
      gunzip(gzipped)
      fail()
    } catch (e: IOException) {
      assertEquals("ISIZE: actual 0x00000020 != expected 0x00123456", e.message)
    }
  }

  @Test
  fun gunzipExhaustsSource() {
    val gzippedSource = Buffer()
      .write("1f8b08000000000000004b4c4a0600c241243503000000".decodeHex()) // 'abc'
    val exhaustableSource = ExhaustableSource(gzippedSource)
    val gunzippedSource = GzipSource(exhaustableSource).buffer()
    assertEquals('a'.code.toLong(), gunzippedSource.readByte().toLong())
    assertEquals('b'.code.toLong(), gunzippedSource.readByte().toLong())
    assertEquals('c'.code.toLong(), gunzippedSource.readByte().toLong())
    assertFalse(exhaustableSource.exhausted)
    assertEquals(-1, gunzippedSource.read(Buffer(), 1))
    assertTrue(exhaustableSource.exhausted)
  }

  @Test
  fun gunzipThrowsIfSourceIsNotExhausted() {
    val gzippedSource = Buffer()
      .write("1f8b08000000000000004b4c4a0600c241243503000000".decodeHex()) // 'abc'
    gzippedSource.writeByte('d'.code) // This byte shouldn't be here!
    val gunzippedSource = GzipSource(gzippedSource).buffer()
    assertEquals('a'.code.toLong(), gunzippedSource.readByte().toLong())
    assertEquals('b'.code.toLong(), gunzippedSource.readByte().toLong())
    assertEquals('c'.code.toLong(), gunzippedSource.readByte().toLong())
    try {
      gunzippedSource.readByte()
      fail()
    } catch (expected: IOException) {
    }
  }

  private fun gzipHeaderWithFlags(flags: Byte): ByteString {
    val result = gzipHeader.toByteArray()
    result[3] = flags
    return of(*result)
  }

  private val gzipHeader = "1f8b0800000000000000".decodeHex()

  // Deflated "It's a UNIX system! I know this!"
  private val deflated = "f32c512f56485408f5f38c5028ae2c2e49cd5554f054c8cecb2f5728c9c82c560400".decodeHex()
  private val gzipTrailer = (
    "" +
      "8d8fad37" + // Checksum of deflated.
      "20000000"
    ) // 32 in little endian.
    .decodeHex()

  private fun gunzip(gzipped: Buffer): Buffer {
    val result = Buffer()
    val source = GzipSource(gzipped)
    while (source.read(result, Int.MAX_VALUE.toLong()) != -1L) {
    }
    return result
  }

  /** This source keeps track of whether its read has returned -1.  */
  internal class ExhaustableSource(private val source: Source) : Source {
    var exhausted = false

    override fun read(sink: Buffer, byteCount: Long): Long {
      val result = source.read(sink, byteCount)
      if (result == -1L) exhausted = true
      return result
    }

    override fun timeout(): Timeout {
      return source.timeout()
    }

    override fun close() {
      source.close()
    }
  }
}
