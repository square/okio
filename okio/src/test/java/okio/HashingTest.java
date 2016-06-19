/*
 * Copyright 2014 Square Inc.
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
package okio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class HashingTest {
  public static final ByteString MD5_abc = ByteString.decodeHex(
      "900150983cd24fb0d6963f7d28e17f72");
  public static final ByteString SHA1_abc = ByteString.decodeHex(
      "a9993e364706816aba3e25717850c26c9cd0d89d");
  public static final ByteString SHA256_abc = ByteString.decodeHex(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  public static final ByteString SHA256_def = ByteString.decodeHex(
      "cb8379ac2098aa165029e3938a51da0bcecfc008fd6795f401178647f96c5b34");
  public static final ByteString SHA256_r32k = ByteString.decodeHex(
      "3a640aa4d129671cb36a4bfbed652d732bce6b7ea83ccdd080c485b8c9ef479d");
  public static final ByteString r32k = TestUtil.randomBytes(32768);

  @Test public void byteStringMd5() {
    assertEquals(MD5_abc, ByteString.encodeUtf8("abc").md5());
  }

  @Test public void byteStringSha1() {
    assertEquals(SHA1_abc, ByteString.encodeUtf8("abc").sha1());
  }

  @Test public void byteStringSha256() {
    assertEquals(SHA256_abc, ByteString.encodeUtf8("abc").sha256());
  }

  @Test public void bufferMd5() {
    assertEquals(MD5_abc, new Buffer().writeUtf8("abc").md5());
  }

  @Test public void bufferSha1() {
    assertEquals(SHA1_abc, new Buffer().writeUtf8("abc").sha1());
  }

  @Test public void bufferSha256() {
    assertEquals(SHA256_abc, new Buffer().writeUtf8("abc").sha256());
  }

  @Test public void bufferHashIsNotDestructive() {
    Buffer buffer = new Buffer();

    buffer.writeUtf8("abc");
    assertEquals(SHA256_abc, buffer.sha256());
    assertEquals("abc", buffer.readUtf8());

    buffer.writeUtf8("def");
    assertEquals(SHA256_def, buffer.sha256());
    assertEquals("def", buffer.readUtf8());

    buffer.write(r32k);
    assertEquals(SHA256_r32k, buffer.sha256());
    assertEquals(r32k, buffer.readByteString());
  }
}
