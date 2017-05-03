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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.annotation.Nullable;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static okio.Util.arrayRangeEquals;
import static okio.Util.checkOffsetAndCount;

/**
 * An immutable sequence of bytes.
 *
 * <p>Byte strings compare lexicographically as a sequence of <strong>unsigned</strong> bytes. That
 * is, the byte string {@code ff} sorts after {@code 00}. This is counter to the sort order of the
 * corresponding bytes, where {@code -1} sorts before {@code 0}.
 *
 * <p><strong>Full disclosure:</strong> this class provides untrusted input and output streams with
 * raw access to the underlying byte array. A hostile stream implementation could keep a reference
 * to the mutable byte string, violating the immutable guarantee of this class. For this reason a
 * byte string's immutability guarantee cannot be relied upon for security in applets and other
 * environments that run both trusted and untrusted code in the same process.
 */
public class ByteString implements Serializable, Comparable<ByteString> {
  static final char[] HEX_DIGITS =
      { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
  private static final long serialVersionUID = 1L;

  /** A singleton empty {@code ByteString}. */
  public static final ByteString EMPTY = ByteString.of();

  final byte[] data;
  transient int hashCode; // Lazily computed; 0 if unknown.
  transient String utf8; // Lazily computed.

  ByteString(byte[] data) {
    this.data = data; // Trusted internal constructor doesn't clone data.
  }

  /**
   * Returns a new byte string containing a clone of the bytes of {@code data}.
   */
  public static ByteString of(byte... data) {
    if (data == null) throw new IllegalArgumentException("data == null");
    return new ByteString(data.clone());
  }

  /**
   * Returns a new byte string containing a copy of {@code byteCount} bytes of {@code data} starting
   * at {@code offset}.
   */
  public static ByteString of(byte[] data, int offset, int byteCount) {
    if (data == null) throw new IllegalArgumentException("data == null");
    checkOffsetAndCount(data.length, offset, byteCount);

    byte[] copy = new byte[byteCount];
    System.arraycopy(data, offset, copy, 0, byteCount);
    return new ByteString(copy);
  }

  public static ByteString of(ByteBuffer data) {
    if (data == null) throw new IllegalArgumentException("data == null");

    byte[] copy = new byte[data.remaining()];
    data.get(copy);
    return new ByteString(copy);
  }

  /** Returns a new byte string containing the {@code UTF-8} bytes of {@code s}. */
  public static ByteString encodeUtf8(String s) {
    if (s == null) throw new IllegalArgumentException("s == null");
    ByteString byteString = new ByteString(s.getBytes(Util.UTF_8));
    byteString.utf8 = s;
    return byteString;
  }

  /** Returns a new byte string containing the {@code charset}-encoded bytes of {@code s}. */
  public static ByteString encodeString(String s, Charset charset) {
    if (s == null) throw new IllegalArgumentException("s == null");
    if (charset == null) throw new IllegalArgumentException("charset == null");
    return new ByteString(s.getBytes(charset));
  }

  /** Constructs a new {@code String} by decoding the bytes as {@code UTF-8}. */
  public String utf8() {
    String result = utf8;
    // We don't care if we double-allocate in racy code.
    return result != null ? result : (utf8 = new String(data, Util.UTF_8));
  }

  /** Constructs a new {@code String} by decoding the bytes using {@code charset}. */
  public String string(Charset charset) {
    if (charset == null) throw new IllegalArgumentException("charset == null");
    return new String(data, charset);
  }

  /**
   * Returns this byte string encoded as <a
   * href="http://www.ietf.org/rfc/rfc2045.txt">Base64</a>. In violation of the
   * RFC, the returned string does not wrap lines at 76 columns.
   */
  public String base64() {
    return Base64.encode(data);
  }

  /** Returns the 128-bit MD5 hash of this byte string. */
  public ByteString md5() {
    return digest("MD5");
  }

  /** Returns the 160-bit SHA-1 hash of this byte string. */
  public ByteString sha1() {
    return digest("SHA-1");
  }

  /** Returns the 256-bit SHA-256 hash of this byte string. */
  public ByteString sha256() {
    return digest("SHA-256");
  }

  /** Returns the 512-bit SHA-512 hash of this byte string. */
  public ByteString sha512() {
    return digest("SHA-512");
  }

  private ByteString digest(String algorithm) {
    try {
      return ByteString.of(MessageDigest.getInstance(algorithm).digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  /** Returns the 160-bit SHA-1 HMAC of this byte string. */
  public ByteString hmacSha1(ByteString key) {
    return hmac("HmacSHA1", key);
  }

  /** Returns the 256-bit SHA-256 HMAC of this byte string. */
  public ByteString hmacSha256(ByteString key) {
    return hmac("HmacSHA256", key);
  }

  /** Returns the 512-bit SHA-512 HMAC of this byte string. */
  public ByteString hmacSha512(ByteString key) {
    return hmac("HmacSHA512", key);
  }

  private ByteString hmac(String algorithm, ByteString key) {
    try {
      Mac mac = Mac.getInstance(algorithm);
      mac.init(new SecretKeySpec(key.toByteArray(), algorithm));
      return ByteString.of(mac.doFinal(data));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Returns this byte string encoded as <a href="http://www.ietf.org/rfc/rfc4648.txt">URL-safe
   * Base64</a>.
   */
  public String base64Url() {
    return Base64.encodeUrl(data);
  }

  /**
   * Decodes the Base64-encoded bytes and returns their value as a byte string.
   * Returns null if {@code base64} is not a Base64-encoded sequence of bytes.
   */
  public static @Nullable ByteString decodeBase64(String base64) {
    if (base64 == null) throw new IllegalArgumentException("base64 == null");
    byte[] decoded = Base64.decode(base64);
    return decoded != null ? new ByteString(decoded) : null;
  }

  /** Returns this byte string encoded in hexadecimal. */
  public String hex() {
    char[] result = new char[data.length * 2];
    int c = 0;
    for (byte b : data) {
      result[c++] = HEX_DIGITS[(b >> 4) & 0xf];
      result[c++] = HEX_DIGITS[b & 0xf];
    }
    return new String(result);
  }

  /** Decodes the hex-encoded bytes and returns their value a byte string. */
  public static ByteString decodeHex(String hex) {
    if (hex == null) throw new IllegalArgumentException("hex == null");
    if (hex.length() % 2 != 0) throw new IllegalArgumentException("Unexpected hex string: " + hex);

    byte[] result = new byte[hex.length() / 2];
    for (int i = 0; i < result.length; i++) {
      int d1 = decodeHexDigit(hex.charAt(i * 2)) << 4;
      int d2 = decodeHexDigit(hex.charAt(i * 2 + 1));
      result[i] = (byte) (d1 + d2);
    }
    return of(result);
  }

  private static int decodeHexDigit(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    throw new IllegalArgumentException("Unexpected hex digit: " + c);
  }

  /**
   * Reads {@code count} bytes from {@code in} and returns the result.
   *
   * @throws java.io.EOFException if {@code in} has fewer than {@code count}
   *     bytes to read.
   */
  public static ByteString read(InputStream in, int byteCount) throws IOException {
    if (in == null) throw new IllegalArgumentException("in == null");
    if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);

    byte[] result = new byte[byteCount];
    for (int offset = 0, read; offset < byteCount; offset += read) {
      read = in.read(result, offset, byteCount - offset);
      if (read == -1) throw new EOFException();
    }
    return new ByteString(result);
  }

  /**
   * Returns a byte string equal to this byte string, but with the bytes 'A'
   * through 'Z' replaced with the corresponding byte in 'a' through 'z'.
   * Returns this byte string if it contains no bytes in 'A' through 'Z'.
   */
  public ByteString toAsciiLowercase() {
    // Search for an uppercase character. If we don't find one, return this.
    for (int i = 0; i < data.length; i++) {
      byte c = data[i];
      if (c < 'A' || c > 'Z') continue;

      // If we reach this point, this string is not not lowercase. Create and
      // return a new byte string.
      byte[] lowercase = data.clone();
      lowercase[i++] = (byte) (c - ('A' - 'a'));
      for (; i < lowercase.length; i++) {
        c = lowercase[i];
        if (c < 'A' || c > 'Z') continue;
        lowercase[i] = (byte) (c - ('A' - 'a'));
      }
      return new ByteString(lowercase);
    }
    return this;
  }

  /**
   * Returns a byte string equal to this byte string, but with the bytes 'a'
   * through 'z' replaced with the corresponding byte in 'A' through 'Z'.
   * Returns this byte string if it contains no bytes in 'a' through 'z'.
   */
  public ByteString toAsciiUppercase() {
    // Search for an lowercase character. If we don't find one, return this.
    for (int i = 0; i < data.length; i++) {
      byte c = data[i];
      if (c < 'a' || c > 'z') continue;

      // If we reach this point, this string is not not uppercase. Create and
      // return a new byte string.
      byte[] lowercase = data.clone();
      lowercase[i++] = (byte) (c - ('a' - 'A'));
      for (; i < lowercase.length; i++) {
        c = lowercase[i];
        if (c < 'a' || c > 'z') continue;
        lowercase[i] = (byte) (c - ('a' - 'A'));
      }
      return new ByteString(lowercase);
    }
    return this;
  }

  /**
   * Returns a byte string that is a substring of this byte string, beginning at the specified
   * index until the end of this string. Returns this byte string if {@code beginIndex} is 0.
   */
  public ByteString substring(int beginIndex) {
    return substring(beginIndex, data.length);
  }

  /**
   * Returns a byte string that is a substring of this byte string, beginning at the specified
   * {@code beginIndex} and ends at the specified {@code endIndex}. Returns this byte string if
   * {@code beginIndex} is 0 and {@code endIndex} is the length of this byte string.
   */
  public ByteString substring(int beginIndex, int endIndex) {
    if (beginIndex < 0) throw new IllegalArgumentException("beginIndex < 0");
    if (endIndex > data.length) {
      throw new IllegalArgumentException("endIndex > length(" + data.length + ")");
    }

    int subLen = endIndex - beginIndex;
    if (subLen < 0) throw new IllegalArgumentException("endIndex < beginIndex");

    if ((beginIndex == 0) && (endIndex == data.length)) {
      return this;
    }

    byte[] copy = new byte[subLen];
    System.arraycopy(data, beginIndex, copy, 0, subLen);
    return new ByteString(copy);
  }

  /** Returns the byte at {@code pos}. */
  public byte getByte(int pos) {
    return data[pos];
  }

  /**
   * Returns the number of bytes in this ByteString.
   */
  public int size() {
    return data.length;
  }

  /**
   * Returns a byte array containing a copy of the bytes in this {@code ByteString}.
   */
  public byte[] toByteArray() {
    return data.clone();
  }

  /** Returns the bytes of this string without a defensive copy. Do not mutate! */
  byte[] internalArray() {
    return data;
  }

  /**
   * Returns a {@code ByteBuffer} view of the bytes in this {@code ByteString}.
   */
  public ByteBuffer asByteBuffer() {
    return ByteBuffer.wrap(data).asReadOnlyBuffer();
  }

  /** Writes the contents of this byte string to {@code out}. */
  public void write(OutputStream out) throws IOException {
    if (out == null) throw new IllegalArgumentException("out == null");
    out.write(data);
  }

  /** Writes the contents of this byte string to {@code buffer}. */
  void write(Buffer buffer) {
    buffer.write(data, 0, data.length);
  }

  /**
   * Returns true if the bytes of this in {@code [offset..offset+byteCount)} equal the bytes of
   * {@code other} in {@code [otherOffset..otherOffset+byteCount)}. Returns false if either range is
   * out of bounds.
   */
  public boolean rangeEquals(int offset, ByteString other, int otherOffset, int byteCount) {
    return other.rangeEquals(otherOffset, this.data, offset, byteCount);
  }

  /**
   * Returns true if the bytes of this in {@code [offset..offset+byteCount)} equal the bytes of
   * {@code other} in {@code [otherOffset..otherOffset+byteCount)}. Returns false if either range is
   * out of bounds.
   */
  public boolean rangeEquals(int offset, byte[] other, int otherOffset, int byteCount) {
    return offset >= 0 && offset <= data.length - byteCount
        && otherOffset >= 0 && otherOffset <= other.length - byteCount
        && arrayRangeEquals(data, offset, other, otherOffset, byteCount);
  }

  public final boolean startsWith(ByteString prefix) {
    return rangeEquals(0, prefix, 0, prefix.size());
  }

  public final boolean startsWith(byte[] prefix) {
    return rangeEquals(0, prefix, 0, prefix.length);
  }

  public final boolean endsWith(ByteString suffix) {
    return rangeEquals(size() - suffix.size(), suffix, 0, suffix.size());
  }

  public final boolean endsWith(byte[] suffix) {
    return rangeEquals(size() - suffix.length, suffix, 0, suffix.length);
  }

  public final int indexOf(ByteString other) {
    return indexOf(other.internalArray(), 0);
  }

  public final int indexOf(ByteString other, int fromIndex) {
    return indexOf(other.internalArray(), fromIndex);
  }

  public final int indexOf(byte[] other) {
    return indexOf(other, 0);
  }

  public int indexOf(byte[] other, int fromIndex) {
    fromIndex = Math.max(fromIndex, 0);
    for (int i = fromIndex, limit = data.length - other.length; i <= limit; i++) {
      if (arrayRangeEquals(data, i, other, 0, other.length)) {
        return i;
      }
    }
    return -1;
  }

  public final int lastIndexOf(ByteString other) {
    return lastIndexOf(other.internalArray(), size());
  }

  public final int lastIndexOf(ByteString other, int fromIndex) {
    return lastIndexOf(other.internalArray(), fromIndex);
  }

  public final int lastIndexOf(byte[] other) {
    return lastIndexOf(other, size());
  }

  public int lastIndexOf(byte[] other, int fromIndex) {
    fromIndex = Math.min(fromIndex, data.length - other.length);
    for (int i = fromIndex; i >= 0; i--) {
      if (arrayRangeEquals(data, i, other, 0, other.length)) {
        return i;
      }
    }
    return -1;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    return o instanceof ByteString
        && ((ByteString) o).size() == data.length
        && ((ByteString) o).rangeEquals(0, data, 0, data.length);
  }

  @Override public int hashCode() {
    int result = hashCode;
    return result != 0 ? result : (hashCode = Arrays.hashCode(data));
  }

  @Override public int compareTo(ByteString byteString) {
    int sizeA = size();
    int sizeB = byteString.size();
    for (int i = 0, size = Math.min(sizeA, sizeB); i < size; i++) {
      int byteA = getByte(i) & 0xff;
      int byteB = byteString.getByte(i) & 0xff;
      if (byteA == byteB) continue;
      return byteA < byteB ? -1 : 1;
    }
    if (sizeA == sizeB) return 0;
    return sizeA < sizeB ? -1 : 1;
  }

  /**
   * Returns a human-readable string that describes the contents of this byte string. Typically this
   * is a string like {@code [text=Hello]} or {@code [hex=0000ffff]}.
   */
  @Override public String toString() {
    if (data.length == 0) {
      return "[size=0]";
    }

    String text = utf8();
    int i = codePointIndexToCharIndex(text, 64);

    if (i == -1) {
      return data.length <= 64
          ? "[hex=" + hex() + "]"
          : "[size=" + data.length + " hex=" + substring(0, 64).hex() + "…]";
    }

    String safeText = text.substring(0, i)
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
    return i < text.length()
        ? "[size=" + data.length + " text=" + safeText + "…]"
        : "[text=" + safeText + "]";
  }

  static int codePointIndexToCharIndex(String s, int codePointCount) {
    for (int i = 0, j = 0, length = s.length(), c; i < length; i += Character.charCount(c)) {
      if (j == codePointCount) {
        return i;
      }
      c = s.codePointAt(i);
      if ((Character.isISOControl(c) && c != '\n' && c != '\r')
          || c == Buffer.REPLACEMENT_CHARACTER) {
        return -1;
      }
      j++;
    }
    return s.length();
  }

  private void readObject(ObjectInputStream in) throws IOException {
    int dataLength = in.readInt();
    ByteString byteString = ByteString.read(in, dataLength);
    try {
      Field field = ByteString.class.getDeclaredField("data");
      field.setAccessible(true);
      field.set(this, byteString.data);
    } catch (NoSuchFieldException e) {
      throw new AssertionError();
    } catch (IllegalAccessException e) {
      throw new AssertionError();
    }
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeInt(data.length);
    out.write(data);
  }
}
