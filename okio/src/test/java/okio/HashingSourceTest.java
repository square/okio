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

import static org.junit.Assert.assertEquals;

public final class HashingSourceTest {
  private static final ByteString MD5_abc = ByteString.decodeHex(
      "900150983cd24fb0d6963f7d28e17f72");
  private static final ByteString SHA1_abc = ByteString.decodeHex(
      "a9993e364706816aba3e25717850c26c9cd0d89d");
  private static final ByteString SHA256_abc = ByteString.decodeHex(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  private static final ByteString SHA256_def = ByteString.decodeHex(
      "cb8379ac2098aa165029e3938a51da0bcecfc008fd6795f401178647f96c5b34");
  private static final ByteString SHA256_x32k = ByteString.decodeHex(
      "427965f49a857174e308658227325dbd23ff4eccbe399d5ad4817dda3ec79f87");
  private static final String x32k = TestUtil.repeat('x', 32768);

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
    assertEquals(SHA256_abc, hashingSource.hash());
    source.writeUtf8("def");
    assertEquals(3L, hashingSource.read(sink, Long.MAX_VALUE));
    assertEquals(SHA256_def, hashingSource.hash());
  }

  @Test public void multipleSegments() throws Exception {
    HashingSource hashingSource = HashingSource.sha256(source);
    BufferedSource bufferedSource = Okio.buffer(hashingSource);
    source.writeUtf8(x32k);
    assertEquals(x32k, bufferedSource.readUtf8());
    assertEquals(SHA256_x32k, hashingSource.hash());
  }

  @Test public void readIntoSuffixOfBuffer() throws Exception {
    HashingSource hashingSource = HashingSource.sha256(source);
    source.writeUtf8(x32k);
    sink.writeUtf8(TestUtil.repeat('z', Segment.SIZE * 2 - 1));
    assertEquals(x32k.length(), hashingSource.read(sink, Long.MAX_VALUE));
    assertEquals(SHA256_x32k, hashingSource.hash());
  }
}
