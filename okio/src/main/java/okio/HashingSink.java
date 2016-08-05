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

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static okio.Util.checkOffsetAndCount;

/**
 * A sink that computes a hash of the full stream of bytes it has accepted. To use, create an
 * instance with your preferred hash algorithm. Write all of the data to the sink and then call
 * {@link #hash()} to compute the final hash value.
 *
 * <p>In this example we use {@code HashingSink} with a {@link BufferedSink} to make writing to the
 * sink easier. <pre>   {@code
 *
 *   HashingSink hashingSink = HashingSink.sha256(s);
 *   BufferedSink bufferedSink = Okio.buffer(hashingSink);
 *
 *   ... // Write to bufferedSink and either flush or close it.
 *
 *   ByteString hash = hashingSink.hash();
 * }</pre>
 */
public final class HashingSink extends ForwardingSink {
  private final MessageDigest messageDigest;

  /** Returns a sink that uses the obsolete MD5 hash algorithm to produce 128-bit hashes. */
  public static HashingSink md5(Sink sink) {
    return new HashingSink(sink, "MD5");
  }

  /** Returns a sink that uses the obsolete SHA-1 hash algorithm to produce 160-bit hashes. */
  public static HashingSink sha1(Sink sink) {
    return new HashingSink(sink, "SHA-1");
  }

  /** Returns a sink that uses the SHA-256 hash algorithm to produce 256-bit hashes. */
  public static HashingSink sha256(Sink sink) {
    return new HashingSink(sink, "SHA-256");
  }

  private HashingSink(Sink sink, String algorithm) {
    super(sink);
    try {
      this.messageDigest = MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError();
    }
  }

  @Override public void write(Buffer source, long byteCount) throws IOException {
    checkOffsetAndCount(source.size, 0, byteCount);

    // Hash byteCount bytes from the prefix of source.
    long hashedCount = 0;
    for (Segment s = source.head; hashedCount < byteCount; s = s.next) {
      int toHash = (int) Math.min(byteCount - hashedCount, s.limit - s.pos);
      messageDigest.update(s.data, s.pos, toHash);
      hashedCount += toHash;
    }

    // Write those bytes to the sink.
    super.write(source, byteCount);
  }

  /**
   * Returns the hash of the bytes accepted thus far and resets the internal state of this sink.
   *
   * <p><strong>Warning:</strong> This method is not idempotent. Each time this method is called its
   * internal state is cleared. This starts a new hash with zero bytes accepted.
   */
  public ByteString hash() {
    byte[] result = messageDigest.digest();
    return ByteString.of(result);
  }
}
