/*
 * Copyright (C) 2016 Square, Inc.
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
import kotlin.test.fail
import okio.HashingSource.Companion.hmacSha1
import okio.HashingSource.Companion.hmacSha256
import okio.HashingSource.Companion.hmacSha512
import okio.HashingSource.Companion.md5
import okio.HashingSource.Companion.sha1
import okio.HashingSource.Companion.sha256

class HashingSourceTest {
  private val source = Buffer()
  private val sink = Buffer()

  @Test fun md5() {
    val hashingSource = md5(source)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(HashingTest.MD5_abc, hashingSource.hash)
  }

  @Test fun sha1() {
    val hashingSource = sha1(source)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(HashingTest.SHA1_abc, hashingSource.hash)
  }

  @Test fun sha256() {
    val hashingSource = sha256(source)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(HashingTest.SHA256_abc, hashingSource.hash)
  }

  @Test fun sha512() {
    val hashingSource: HashingSource = HashingSource.sha512(source)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(HashingTest.SHA512_abc, hashingSource.hash)
  }

  @Test fun hmacSha1() {
    val hashingSource = hmacSha1(source, HashingTest.HMAC_KEY)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(HashingTest.HMAC_SHA1_abc, hashingSource.hash)
  }

  @Test fun hmacSha256() {
    val hashingSource = hmacSha256(source, HashingTest.HMAC_KEY)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(HashingTest.HMAC_SHA256_abc, hashingSource.hash)
  }

  @Test fun hmacSha512() {
    val hashingSource = hmacSha512(source, HashingTest.HMAC_KEY)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(HashingTest.HMAC_SHA512_abc, hashingSource.hash)
  }

  @Test fun multipleReads() {
    val hashingSource = sha256(source)
    val bufferedSource = hashingSource.buffer()
    source.writeUtf8("a")
    assertEquals('a'.code.toLong(), bufferedSource.readUtf8CodePoint().toLong())
    source.writeUtf8("b")
    assertEquals('b'.code.toLong(), bufferedSource.readUtf8CodePoint().toLong())
    source.writeUtf8("c")
    assertEquals('c'.code.toLong(), bufferedSource.readUtf8CodePoint().toLong())
    assertEquals(HashingTest.SHA256_abc, hashingSource.hash)
  }

  @Test fun multipleHashes() {
    val hashingSource = sha256(source)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    val hash_abc = hashingSource.hash
    assertEquals(HashingTest.SHA256_abc, hash_abc)
    source.writeUtf8("def")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(HashingTest.SHA256_def, hashingSource.hash)
    assertEquals(HashingTest.SHA256_abc, hash_abc)
  }

  @Test fun multipleSegments() {
    val hashingSource = sha256(source)
    val bufferedSource = hashingSource.buffer()
    source.write(HashingTest.r32k)
    assertEquals(HashingTest.r32k, bufferedSource.readByteString())
    assertEquals(HashingTest.SHA256_r32k, hashingSource.hash)
  }

  @Test fun readIntoSuffixOfBuffer() {
    val hashingSource = sha256(source)
    source.write(HashingTest.r32k)
    sink.writeUtf8("z".repeat(Segment.SIZE * 2 - 1))
    assertEquals(HashingTest.r32k.size.toLong(), hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(HashingTest.SHA256_r32k, hashingSource.hash)
  }

  @Test fun hmacEmptyKey() {
    try {
      hmacSha256(source, ByteString.EMPTY)
      fail()
    } catch (expected: IllegalArgumentException) {
    }
  }
}
