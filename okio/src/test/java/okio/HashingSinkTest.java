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

public final class HashingSinkTest {
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
    source.writeUtf8(x32k);
    hashingSink.write(source, x32k.length());
    assertEquals(SHA256_x32k, hashingSink.hash());
  }

  @Test public void readFromPrefixOfBuffer() throws Exception {
    source.writeUtf8("z");
    source.writeUtf8(x32k);
    source.skip(1);
    source.writeUtf8(TestUtil.repeat('z', Segment.SIZE * 2 - 1));
    HashingSink hashingSink = HashingSink.sha256(sink);
    hashingSink.write(source, x32k.length());
    assertEquals(SHA256_x32k, hashingSink.hash());
  }
}
