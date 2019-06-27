/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package okio

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class HashingSourceTest {
  private val source = Buffer()
  private val sink = Buffer()

  @Test fun md5() {
    val hashingSource = HashingSource.md5(source)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(MD5_abc, hashingSource.hash)
  }

  @Test fun sha1() {
    val hashingSource = HashingSource.sha1(source)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(SHA1_abc, hashingSource.hash)
  }

  @Test fun sha256() {
    val hashingSource = HashingSource.sha256(source)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(SHA256_abc, hashingSource.hash)
  }

  @Test fun sha512() {
    val hashingSource = HashingSource.sha512(source)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(SHA512_abc, hashingSource.hash)
  }

  @Test fun hmacSha1() {
    val hashingSource = HashingSource.hmacSha1(source, HMAC_KEY)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(HMAC_SHA1_abc, hashingSource.hash)
  }

  @Test fun hmacSha256() {
    val hashingSource = HashingSource.hmacSha256(source, HMAC_KEY)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(HMAC_SHA256_abc, hashingSource.hash)
  }

  @Test fun hmacSha512() {
    val hashingSource = HashingSource.hmacSha512(source, HMAC_KEY)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(HMAC_SHA512_abc, hashingSource.hash)
  }

  @Ignore // TODO: implement buffer()
  @Test fun multipleReads() {
    val hashingSource = HashingSource.sha256(source)
    val bufferedSource = hashingSource.buffer()
    source.writeUtf8("a")
    assertEquals('a'.toInt(), bufferedSource.readUtf8CodePoint())
    source.writeUtf8("b")
    assertEquals('b'.toInt(), bufferedSource.readUtf8CodePoint())
    source.writeUtf8("c")
    assertEquals('c'.toInt(), bufferedSource.readUtf8CodePoint())
    assertEquals(SHA256_abc, hashingSource.hash)
  }

  @Test fun multipleHashes() {
    val hashingSource = HashingSource.sha256(source)
    source.writeUtf8("abc")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    val hash_abc = hashingSource.hash
    assertEquals(SHA256_abc, hash_abc, "abc")
    source.writeUtf8("def")
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE))
    assertEquals(SHA256_def, hashingSource.hash, "def")
    assertEquals(SHA256_abc, hash_abc, "abc")
  }

//  @Test fun multipleSegments() {
//    val hashingSource = HashingSource.sha256(source)
//    val bufferedSource = hashingSource.buffer()
//    source.write(r32k)
//    assertEquals(r32k, bufferedSource.readByteString())
//    assertEquals(SHA256_r32k, hashingSource.hash)
//  }
//
//  @Test fun readIntoSuffixOfBuffer() {
//    val hashingSource = HashingSource.sha256(source)
//    source.write(r32k)
//    sink.writeUtf8(TestUtil.repeat('z', SEGMENT_SIZE * 2 - 1))
//    assertEquals(r32k.size().toLong(), hashingSource.read(sink, Long.MAX_VALUE))
//    assertEquals(SHA256_r32k, hashingSource.hash)
//  }

  @Test fun hmacEmptyKey() {
    try {
      HashingSource.hmacSha256(source, ByteString.EMPTY)
      fail()
    } catch (expected: IllegalArgumentException) {
    }
  }
}
