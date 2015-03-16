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

import java.io.EOFException;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import org.junit.Test;

import static okio.TestUtil.randomBytes;
import static okio.TestUtil.repeat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class InflaterSourceTest {
  @Test public void inflate() throws Exception {
    Buffer deflated = decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tK"
        + "tYDAF6CD5s=");
    Buffer inflated = inflate(deflated);
    assertEquals("God help us, we're in the hands of engineers.", inflated.readUtf8());
  }

  @Test public void inflateTruncated() throws Exception {
    Buffer deflated = decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tK"
        + "tYDAF6CDw==");
    try {
      inflate(deflated);
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void inflateWellCompressed() throws Exception {
    Buffer deflated = decodeBase64("eJztwTEBAAAAwqCs61/CEL5AAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB8B"
        + "tFeWvE=\n");
    String original = repeat('a', 1024 * 1024);
    Buffer inflated = inflate(deflated);
    assertEquals(original, inflated.readUtf8());
  }

  @Test public void inflatePoorlyCompressed() throws Exception {
    ByteString original = randomBytes(1024 * 1024);
    Buffer deflated = deflate(original);
    Buffer inflated = inflate(deflated);
    assertEquals(original, inflated.readByteString());
  }

  @Test public void inflateIntoNonemptySink() throws Exception {
    for (int i = 0; i < Segment.SIZE; i++) {
      Buffer inflated = new Buffer().writeUtf8(repeat('a', i));
      Buffer deflated = decodeBase64(
          "eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=");
      InflaterSource source = new InflaterSource(deflated, new Inflater());
      while (source.read(inflated, Integer.MAX_VALUE) != -1) {
      }
      inflated.skip(i);
      assertEquals("God help us, we're in the hands of engineers.", inflated.readUtf8());
    }
  }

  private Buffer decodeBase64(String s) {
    return new Buffer().write(ByteString.decodeBase64(s));
  }

  /** Use DeflaterOutputStream to deflate source. */
  private Buffer deflate(ByteString source) throws IOException {
    Buffer result = new Buffer();
    Sink sink = Okio.sink(new DeflaterOutputStream(result.outputStream()));
    sink.write(new Buffer().write(source), source.size());
    sink.close();
    return result;
  }

  /** Returns a new buffer containing the inflated contents of {@code deflated}. */
  private Buffer inflate(Buffer deflated) throws IOException {
    Buffer result = new Buffer();
    InflaterSource source = new InflaterSource(deflated, new Inflater());
    while (source.read(result, Integer.MAX_VALUE) != -1) {
    }
    return result;
  }
}
