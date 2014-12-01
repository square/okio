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
package okio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class Utf8Test {
  @Test public void oneByteCharacters() throws Exception {
    assertEncoded("00", 0x00); // Smallest 1-byte character.
    assertEncoded("20", ' ');
    assertEncoded("7e", '~');
    assertEncoded("7f", 0x7f); // Largest 1-byte character.
  }

  @Test public void twoByteCharacters() throws Exception {
    assertEncoded("c280", 0x0080); // Smallest 2-byte character.
    assertEncoded("c3bf", 0x00ff);
    assertEncoded("c480", 0x0100);
    assertEncoded("dfbf", 0x07ff); // Largest 2-byte character.
  }

  @Test public void threeByteCharacters() throws Exception {
    assertEncoded("e0a080", 0x0800); // Smallest 3-byte character.
    assertEncoded("e0bfbf", 0x0fff);
    assertEncoded("e18080", 0x1000);
    assertEncoded("e1bfbf", 0x1fff);
    assertEncoded("ed8080", 0xd000);
    assertEncoded("ed9fbf", 0xd7ff); // Largest character lower than the min surrogate.
    assertEncoded("ee8080", 0xe000); // Smallest character greater than the max surrogate.
    assertEncoded("eebfbf", 0xefff);
    assertEncoded("ef8080", 0xf000);
    assertEncoded("efbfbf", 0xffff); // Largest 3-byte character.
  }

  @Test public void fourByteCharacters() throws Exception {
    assertEncoded("f0908080", 0x010000); // Smallest surrogate pair.
    assertEncoded("f48fbfbf", 0x10ffff); // Largest code point expressible by UTF-16.
  }

  @Test public void danglingHighSurrogate() throws Exception {
    assertEncoded("3f", "\ud800"); // "?"
  }

  @Test public void lowSurrogateWithoutHighSurrogate() throws Exception {
    assertEncoded("3f", "\udc00"); // "?"
  }

  @Test public void highSurrogateFollowedByNonSurrogate() throws Exception {
    assertEncoded("3f61", "\ud800\u0061"); // "?a": Following character is too low.
    assertEncoded("3fee8080", "\ud800\ue000"); // "?\ue000": Following character is too high.
  }

  @Test public void multipleSegmentString() throws Exception {
    String a = TestUtil.repeat('a', Segment.SIZE + Segment.SIZE + 1);
    Buffer encoded = new Buffer().writeUtf8(a);
    Buffer expected = new Buffer().write(a.getBytes(Util.UTF_8));
    assertEquals(expected, encoded);
  }

  @Test public void stringSpansSegments() throws Exception {
    Buffer buffer = new Buffer();
    String a = TestUtil.repeat('a', Segment.SIZE - 1);
    String b = "bb";
    String c = TestUtil.repeat('c', Segment.SIZE - 1);
    buffer.writeUtf8(a);
    buffer.writeUtf8(b);
    buffer.writeUtf8(c);
    assertEquals(a + b + c, buffer.readUtf8());
  }

  private void assertEncoded(String hex, int... codePoints) throws Exception {
    assertEncoded(hex, new String(codePoints, 0, codePoints.length));
  }

  private void assertEncoded(String hex, String string) throws Exception {
    ByteString expectedUtf8 = ByteString.decodeHex(hex);

    // Confirm our expectations are consistent with the platform.
    ByteString platformUtf8 = ByteString.of(string.getBytes("UTF-8"));
    assertEquals(expectedUtf8, platformUtf8);

    // Confirm our implementation matches those expectations.
    ByteString actualUtf8 = new Buffer().writeUtf8(string).readByteString();
    assertEquals(expectedUtf8, actualUtf8);
  }
}
