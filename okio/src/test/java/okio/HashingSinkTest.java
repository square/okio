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
import static okio.HashingTest.r32k;
import static okio.HashingTest.SHA512_abc;
import static org.junit.Assert.assertEquals;

public final class HashingSinkTest {
  private final Buffer source = new Buffer();
  private final Buffer sink = new Buffer();

  @Test public void md5() throws Exception {
    HashingSink hashingSink = HashingSink.md5(sink);
    source.writeUtf8("abc");
    hashingSink.write(source, 3L);
    assertEquals(MD5_abc, hashingSink.hash());
  }

  @Test public void sha1() throws Exception {
    HashingSink hashingSink = HashingSink.sha1(sink);
    source.writeUtf8("abc");
    hashingSink.write(source, 3L);
    assertEquals(SHA1_abc, hashingSink.hash());
  }

  @Test public void sha256() throws Exception {
    HashingSink hashingSink = HashingSink.sha256(sink);
    source.writeUtf8("abc");
    hashingSink.write(source, 3L);
    assertEquals(SHA256_abc, hashingSink.hash());
  }

  @Test public void sha512() throws Exception {
    HashingSink hashingSink = HashingSink.sha512(sink);
    source.writeUtf8("abc");
    hashingSink.write(source, 3L);
    assertEquals(SHA512_abc, hashingSink.hash());
  }

  @Test public void hmacSha1() throws Exception {
    HashingSink hashingSink = HashingSink.hmacSha1(sink, HMAC_KEY);
    source.writeUtf8("abc");
    hashingSink.write(source, 3L);
    assertEquals(HMAC_SHA1_abc, hashingSink.hash());
  }

  @Test public void hmacSha256() throws Exception {
    HashingSink hashingSink = HashingSink.hmacSha256(sink, HMAC_KEY);
    source.writeUtf8("abc");
    hashingSink.write(source, 3L);
    assertEquals(HMAC_SHA256_abc, hashingSink.hash());
  }

  @Test public void hmacSha512() throws Exception {
    HashingSink hashingSink = HashingSink.hmacSha512(sink, HMAC_KEY);
    source.writeUtf8("abc");
    hashingSink.write(source, 3L);
    assertEquals(HMAC_SHA512_abc, hashingSink.hash());
  }

  @Test public void multipleWrites() throws Exception {
    HashingSink hashingSink = HashingSink.sha256(sink);
    source.writeUtf8("a");
    hashingSink.write(source, 1L);
    source.writeUtf8("b");
    hashingSink.write(source, 1L);
    source.writeUtf8("c");
    hashingSink.write(source, 1L);
    assertEquals(SHA256_abc, hashingSink.hash());
  }

  @Test public void multipleHashes() throws Exception {
    HashingSink hashingSink = HashingSink.sha256(sink);
    source.writeUtf8("abc");
    hashingSink.write(source, 3L);
    assertEquals(SHA256_abc, hashingSink.hash());
    source.writeUtf8("def");
    hashingSink.write(source, 3L);
    assertEquals(SHA256_def, hashingSink.hash());
  }

  @Test public void multipleSegments() throws Exception {
    HashingSink hashingSink = HashingSink.sha256(sink);
    source.write(r32k);
    hashingSink.write(source, r32k.size());
    assertEquals(SHA256_r32k, hashingSink.hash());
  }

  @Test public void readFromPrefixOfBuffer() throws Exception {
    source.writeUtf8("z");
    source.write(r32k);
    source.skip(1);
    source.writeUtf8(TestUtil.repeat('z', Segment.SIZE * 2 - 1));
    HashingSink hashingSink = HashingSink.sha256(sink);
    hashingSink.write(source, r32k.size());
    assertEquals(SHA256_r32k, hashingSink.hash());
  }
}
