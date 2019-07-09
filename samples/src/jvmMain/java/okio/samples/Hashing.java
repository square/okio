/*
 * Copyright (C) 2018 Square, Inc.
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
package okio.samples;

import java.io.File;
import java.io.IOException;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;
import okio.HashingSink;
import okio.HashingSource;
import okio.Okio;
import okio.Source;

public final class Hashing {
  public void run() throws Exception {
    File file = new File("README.md");

    System.out.println("ByteString");
    ByteString byteString = readByteString(file);
    System.out.println("       md5: " + byteString.md5().hex());
    System.out.println("      sha1: " + byteString.sha1().hex());
    System.out.println("    sha256: " + byteString.sha256().hex());
    System.out.println("    sha512: " + byteString.sha512().hex());
    System.out.println();

    System.out.println("Buffer");
    Buffer buffer = readBuffer(file);
    System.out.println("       md5: " + buffer.md5().hex());
    System.out.println("      sha1: " + buffer.sha1().hex());
    System.out.println("    sha256: " + buffer.sha256().hex());
    System.out.println("    sha512: " + buffer.sha512().hex());
    System.out.println();

    System.out.println("HashingSource");
    try (HashingSource hashingSource = HashingSource.sha256(Okio.source(file));
         BufferedSource source = Okio.buffer(hashingSource)) {
      source.readAll(Okio.blackhole());
      System.out.println("    sha256: " + hashingSource.hash().hex());
    }
    System.out.println();

    System.out.println("HashingSink");
    try (HashingSink hashingSink = HashingSink.sha256(Okio.blackhole());
         BufferedSink sink = Okio.buffer(hashingSink);
         Source source = Okio.source(file)) {
      sink.writeAll(source);
      sink.close(); // Emit anything buffered.
      System.out.println("    sha256: " + hashingSink.hash().hex());
    }
    System.out.println();

    System.out.println("HMAC");
    ByteString secret = ByteString.decodeHex("7065616e7574627574746572");
    System.out.println("hmacSha256: " + byteString.hmacSha256(secret).hex());
    System.out.println();
  }

  public ByteString readByteString(File file) throws IOException {
    try (BufferedSource source = Okio.buffer(Okio.source(file))) {
      return source.readByteString();
    }
  }

  public Buffer readBuffer(File file) throws IOException {
    try (Source source = Okio.source(file)) {
      Buffer buffer = new Buffer();
      buffer.writeAll(source);
      return buffer;
    }
  }

  public static void main(String[] args) throws Exception {
    new Hashing().run();
  }
}
