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
  public static final ByteString HMAC_KEY = ByteString.decodeHex("0102030405060708");
  public static final ByteString MD5_abc = ByteString.decodeHex(
      "900150983cd24fb0d6963f7d28e17f72");
  public static final ByteString SHA1_abc = ByteString.decodeHex(
      "a9993e364706816aba3e25717850c26c9cd0d89d");
  public static final ByteString HMAC_SHA1_abc = ByteString.decodeHex(
      "987af8649982ff7d9fbb1b8aa35099146997af51");
  public static final ByteString SHA256_abc = ByteString.decodeHex(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  public static final ByteString SHA256_empty = ByteString.decodeHex(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  public static final ByteString SHA256_def = ByteString.decodeHex(
      "cb8379ac2098aa165029e3938a51da0bcecfc008fd6795f401178647f96c5b34");
  public static final ByteString SHA256_r32k = ByteString.decodeHex(
      "3a640aa4d129671cb36a4bfbed652d732bce6b7ea83ccdd080c485b8c9ef479d");
  public static final ByteString SHA512_abc = ByteString.decodeHex(
          "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f");
  public static final ByteString SHA512_empty = ByteString.decodeHex(
          "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e");
  public static final ByteString HMAC_SHA256_empty = ByteString.decodeHex(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  public static final ByteString HMAC_SHA256_abc = ByteString.decodeHex(
      "446d1715583cf1c30dfffbec0df4ff1f9d39d493211ab4c97ed6f3f0eb579b47");
  public static final ByteString HMAC_SHA512_empty = ByteString.decodeHex(
          "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e");
  public static final ByteString HMAC_SHA512_abc = ByteString.decodeHex(
          "24391790e7131050b05b606f2079a8983313894a1642a5ed97d094e7cabd00cfaa857d92c1f320ca3b6aaabb84c7155d6f1b10940dc133ded1b40baee8900be6");
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

  @Test public void byteStringSha512() {
    assertEquals(SHA512_abc, ByteString.encodeUtf8("abc").sha512());
  }

  @Test public void byteStringHmacSha1() {
    assertEquals(HMAC_SHA1_abc, ByteString.encodeUtf8("abc").hmacSha1(HMAC_KEY));
  }

  @Test public void byteStringHmacSha256() {
    assertEquals(HMAC_SHA256_abc, ByteString.encodeUtf8("abc").hmacSha256(HMAC_KEY));
  }

  @Test public void byteStringHmacSha512() {
    assertEquals(HMAC_SHA512_abc, ByteString.encodeUtf8("abc").hmacSha512(HMAC_KEY));
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

  @Test public void bufferSha512() {
    assertEquals(SHA512_abc, new Buffer().writeUtf8("abc").sha512());
  }

  @Test public void hashEmptySha256Buffer() {
    assertEquals(SHA256_empty, new Buffer().sha256());
  }

  @Test public void hashEmptySha512Buffer() {
    assertEquals(SHA512_empty, new Buffer().sha512());
  }

  @Test public void bufferHmacSha1() {
    assertEquals(HMAC_SHA1_abc, new Buffer().writeUtf8("abc").hmacSha1(HMAC_KEY));
  }

  @Test public void bufferHmacSha256() {
    assertEquals(HMAC_SHA256_abc, new Buffer().writeUtf8("abc").hmacSha256(HMAC_KEY));
  }

  @Test public void bufferHmacSha512() {
    assertEquals(HMAC_SHA512_abc, new Buffer().writeUtf8("abc").hmacSha512(HMAC_KEY));
  }

  @Test public void hmacSha256EmptyBuffer() {
    assertEquals(HMAC_SHA256_empty, new Buffer().sha256());
  }

  @Test public void hmacSha512EmptyBuffer() {
    assertEquals(HMAC_SHA512_empty, new Buffer().sha512());
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
