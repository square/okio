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

/**
 * A source that computes a hash of the full stream of bytes it has supplied. To use, create an
 * instance with your preferred hash algorithm. Exhaust the source by reading all of its bytes and
 * then call {@link #hash()} to compute the final hash value.
 *
 * <p>In this example we use {@code HashingSource} with a {@link BufferedSource} to make reading
 * from the source easier. <pre>   {@code
 *
 *   HashingSource hashingSource = HashingSource.sha256(rawSource);
 *   BufferedSource bufferedSource = Okio.buffer(hashingSource);
 *
 *   ... // Read all of bufferedSource.
 *
 *   ByteString hash = hashingSource.hash();
 * }</pre>
 */
public final class HashingSource extends ForwardingSource {
  private final MessageDigest messageDigest;

  /** Returns a sink that uses the obsolete MD5 hash algorithm to produce 128-bit hashes. */
  public static HashingSource md5(Source source) {
    return new HashingSource(source, "MD5");
  }

  /** Returns a sink that uses the obsolete SHA-1 hash algorithm to produce 160-bit hashes. */
  public static HashingSource sha1(Source source) {
    return new HashingSource(source, "SHA-1");
  }

  /** Returns a sink that uses the SHA-256 hash algorithm to produce 256-bit hashes. */
  public static HashingSource sha256(Source source) {
    return new HashingSource(source, "SHA-256");
  }

  private HashingSource(Source source, String algorithm) {
    super(source);
    try {
      this.messageDigest = MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError();
    }
  }

  @Override public long read(Buffer sink, long byteCount) throws IOException {
    long result = super.read(sink, byteCount);

    if (result != -1L) {
      long start = sink.size - result;

      // Find the first segment that has new bytes.
      long offset = sink.size;
      Segment s = sink.head;
      while (offset > start) {
        s = s.prev;
        offset -= (s.limit - s.pos);
      }

      // Hash that segment and all the rest until the end.
      while (offset < sink.size) {
        int pos = (int) (s.pos + start - offset);
        messageDigest.update(s.data, pos, s.limit - pos);
        offset += (s.limit - s.pos);
        start = offset;
        s = s.next;
      }
    }

    return result;
  }

  /**
   * Returns the hash of the bytes supplied thus far and resets the internal state of this source.
   *
   * <p><strong>Warning:</strong> This method is not idempotent. Each time this method is called its
   * internal state is cleared. This starts a new hash with zero bytes supplied.
   */
  public ByteString hash() {
    byte[] result = messageDigest.digest();
    return ByteString.of(result);
  }
}
