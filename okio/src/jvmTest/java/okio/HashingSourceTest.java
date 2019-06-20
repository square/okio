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
package okio;

import org.junit.Test;

import static okio.HashingTest.HMAC_KEY;
import static okio.HashingTest.HMAC_SHA1_abc;
import static okio.HashingTest.HMAC_SHA256_abc;
import static okio.HashingTest.HMAC_SHA512_abc;
import static okio.HashingTest.MD5_abc;
import static okio.HashingTest.SHA1_abc;
import static okio.HashingTest.SHA256_abc;
import static okio.HashingTest.SHA256_def;
import static okio.HashingTest.SHA256_r32k;
import static okio.HashingTest.SHA512_abc;
import static okio.HashingTest.r32k;
import static okio.TestUtil.SEGMENT_SIZE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class HashingSourceTest {
  private final Buffer source = new Buffer();
  private final Buffer sink = new Buffer();

  @Test public void md5() throws Exception {
    HashingSource hashingSource = HashingSource.md5(source);
    source.writeUtf8("abc");
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE));
    assertEquals(MD5_abc, hashingSource.hash());
  }

  @Test public void sha1() throws Exception {
    HashingSource hashingSource = HashingSource.sha1(source);
    source.writeUtf8("abc");
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE));
    assertEquals(SHA1_abc, hashingSource.hash());
  }

  @Test public void sha256() throws Exception {
    HashingSource hashingSource = HashingSource.sha256(source);
    source.writeUtf8("abc");
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE));
    assertEquals(SHA256_abc, hashingSource.hash());
  }

  @Test public void sha512() throws Exception {
    HashingSource hashingSource = HashingSource.sha512(source);
    source.writeUtf8("abc");
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE));
    assertEquals(SHA512_abc, hashingSource.hash());
  }

  @Test public void hmacSha1() throws Exception {
    HashingSource hashingSource = HashingSource.hmacSha1(source, HMAC_KEY);
    source.writeUtf8("abc");
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE));
    assertEquals(HMAC_SHA1_abc, hashingSource.hash());
  }

  @Test public void hmacSha256() throws Exception {
    HashingSource hashingSource = HashingSource.hmacSha256(source, HMAC_KEY);
    source.writeUtf8("abc");
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE));
    assertEquals(HMAC_SHA256_abc, hashingSource.hash());
  }

  @Test public void hmacSha512() throws Exception {
    HashingSource hashingSource = HashingSource.hmacSha512(source, HMAC_KEY);
    source.writeUtf8("abc");
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE));
    assertEquals(HMAC_SHA512_abc, hashingSource.hash());
  }

  @Test public void multipleReads() throws Exception {
    HashingSource hashingSource = HashingSource.sha256(source);
    BufferedSource bufferedSource = Okio.buffer(hashingSource);
    source.writeUtf8("a");
    assertEquals('a', bufferedSource.readUtf8CodePoint());
    source.writeUtf8("b");
    assertEquals('b', bufferedSource.readUtf8CodePoint());
    source.writeUtf8("c");
    assertEquals('c', bufferedSource.readUtf8CodePoint());
    assertEquals(SHA256_abc, hashingSource.hash());
  }

  @Test public void multipleHashes() throws Exception {
    HashingSource hashingSource = HashingSource.sha256(source);
    source.writeUtf8("abc");
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE));
    ByteString hash_abc = hashingSource.hash();
    assertEquals(SHA256_abc, hash_abc);
    source.writeUtf8("def");
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE));
    assertEquals(SHA256_def, hashingSource.hash());
    assertEquals(SHA256_abc, hash_abc);
  }

  @Test public void multipleSegments() throws Exception {
    HashingSource hashingSource = HashingSource.sha256(source);
    BufferedSource bufferedSource = Okio.buffer(hashingSource);
    source.write(r32k);
    assertEquals(r32k, bufferedSource.readByteString());
    assertEquals(SHA256_r32k, hashingSource.hash());
  }

  @Test public void readIntoSuffixOfBuffer() throws Exception {
    HashingSource hashingSource = HashingSource.sha256(source);
    source.write(r32k);
    sink.writeUtf8(TestUtil.repeat('z', SEGMENT_SIZE * 2 - 1));
    assertEquals(r32k.size(), hashingSource.read(sink, Long.MAX_VALUE));
    assertEquals(SHA256_r32k, hashingSource.hash());
  }

  @Test public void hmacEmptyKey() throws Exception {
    try {
      HashingSource.hmacSha256(source, ByteString.EMPTY);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }
}
