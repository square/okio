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
package okio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class OptionsTest {
  /** Confirm that options prefers the first-listed option, not the longest or shortest one. */
  @Test public void optionOrderTakesPrecedence() {
    assertSelect("abcdefg", 0, "abc", "abcdef");
    assertSelect("abcdefg", 0, "abcdef", "abc");
  }

  @Test public void simpleOptionsTrie() {
    assertEquals(trieString(utf8Options("hotdog", "hoth", "hot")), ""
        + "hot\n"
        + "   -> 2\n"
        + "   d\n"
        + "    og -> 0\n"
        + "   h -> 1\n");
  }

  @Test public void realisticOptionsTrie() {
    // These are the fields of OkHttpClient in 3.10.
    Options options = utf8Options(
        "dispatcher",
        "proxy",
        "protocols",
        "connectionSpecs",
        "interceptors",
        "networkInterceptors",
        "eventListenerFactory",
        "proxySelector", // No index 7 in the trie because 'proxy' is a prefix!
        "cookieJar",
        "cache",
        "internalCache",
        "socketFactory",
        "sslSocketFactory",
        "certificateChainCleaner",
        "hostnameVerifier",
        "certificatePinner",
        "proxyAuthenticator", // No index 16 in the trie because 'proxy' is a prefix!
        "authenticator",
        "connectionPool",
        "dns",
        "followSslRedirects",
        "followRedirects",
        "retryOnConnectionFailure",
        "connectTimeout",
        "readTimeout",
        "writeTimeout",
        "pingInterval");
    assertEquals(trieString(options), ""
            + "a\n"
            + " uthenticator -> 17\n"
            + "c\n"
            + " a\n"
            + "  che -> 9\n"
            + " e\n"
            + "  rtificate\n"
            + "           C\n"
            + "            hainCleaner -> 13\n"
            + "           P\n"
            + "            inner -> 15\n"
            + " o\n"
            + "  n\n"
            + "   nect\n"
            + "       T\n"
            + "        imeout -> 23\n"
            + "       i\n"
            + "        on\n"
            + "          P\n"
            + "           ool -> 18\n"
            + "          S\n"
            + "           pecs -> 3\n"
            + "  o\n"
            + "   kieJar -> 8\n"
            + "d\n"
            + " i\n"
            + "  spatcher -> 0\n"
            + " n\n"
            + "  s -> 19\n"
            + "e\n"
            + " ventListenerFactory -> 6\n"
            + "f\n"
            + " ollow\n"
            + "      R\n"
            + "       edirects -> 21\n"
            + "      S\n"
            + "       slRedirects -> 20\n"
            + "h\n"
            + " ostnameVerifier -> 14\n"
            + "i\n"
            + " nter\n"
            + "     c\n"
            + "      eptors -> 4\n"
            + "     n\n"
            + "      alCache -> 10\n"
            + "n\n"
            + " etworkInterceptors -> 5\n"
            + "p\n"
            + " i\n"
            + "  ngInterval -> 26\n"
            + " r\n"
            + "  o\n"
            + "   t\n"
            + "    ocols -> 2\n"
            + "   x\n"
            + "    y -> 1\n"
            + "r\n"
            + " e\n"
            + "  a\n"
            + "   dTimeout -> 24\n"
            + "  t\n"
            + "   ryOnConnectionFailure -> 22\n"
            + "s\n"
            + " o\n"
            + "  cketFactory -> 11\n"
            + " s\n"
            + "  lSocketFactory -> 12\n"
            + "w\n"
            + " riteTimeout -> 25\n");
        assertSelect("", -1, options);
        assertSelect("a", -1, options);
        assertSelect("eventListenerFactor", -1, options);
        assertSelect("dnst", 19, options);
        assertSelect("proxyproxy", 1, options);
        assertSelect("prox", -1, options);

        assertSelect("dispatcher", 0, options);
        assertSelect("proxy", 1, options);
        assertSelect("protocols", 2, options);
        assertSelect("connectionSpecs", 3, options);
        assertSelect("interceptors", 4, options);
        assertSelect("networkInterceptors", 5, options);
        assertSelect("eventListenerFactory", 6, options);
        assertSelect("proxySelector", 1, options); // 'proxy' is a prefix.
        assertSelect("cookieJar", 8, options);
        assertSelect("cache", 9, options);
        assertSelect("internalCache", 10, options);
        assertSelect("socketFactory", 11, options);
        assertSelect("sslSocketFactory", 12, options);
        assertSelect("certificateChainCleaner", 13, options);
        assertSelect("hostnameVerifier", 14, options);
        assertSelect("certificatePinner", 15, options);
        assertSelect("proxyAuthenticator", 1, options); // 'proxy' is a prefix.
        assertSelect("authenticator", 17, options);
        assertSelect("connectionPool", 18, options);
        assertSelect("dns", 19, options);
        assertSelect("followSslRedirects", 20, options);
        assertSelect("followRedirects", 21, options);
        assertSelect("retryOnConnectionFailure", 22, options);
        assertSelect("connectTimeout", 23, options);
        assertSelect("readTimeout", 24, options);
        assertSelect("writeTimeout", 25, options);
        assertSelect("pingInterval", 26, options);
  }

  @Test public void emptyOptions() {
    Options options = utf8Options();
    assertSelect("", -1, options);
    assertSelect("a", -1, options);
    assertSelect("abc", -1, options);
  }

  @Test public void emptyStringInOptionsTrie() {
    try {
      utf8Options("");
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      utf8Options("abc", "");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void multipleIdenticalValues() {
    try {
      utf8Options("abc", "abc");
      fail();
    } catch (IllegalArgumentException expected) {
      assertEquals(expected.getMessage(), "duplicate option: [text=abc]");
    }
  }

  @Test public void prefixesAreStripped() {
    Options options = utf8Options("abcA", "abc", "abcB");
    assertEquals(trieString(options), ""
        + "abc\n"
        + "   -> 1\n"
        + "   A -> 0\n");
    assertSelect("abc", 1, options);
    assertSelect("abcA", 0, options);
    assertSelect("abcB", 1, options);
    assertSelect("abcC", 1, options);
    assertSelect("ab", -1, options);
  }

  @Test public void multiplePrefixesAreStripped() {
    assertEquals(trieString(utf8Options("a", "ab", "abc", "abcd", "abcde")), ""
        + "a -> 0\n");
    assertEquals(trieString(utf8Options("abc", "a", "ab", "abe", "abcd", "abcf")), ""
        + "a\n"
        + " -> 1\n"
        + " bc -> 0\n");
    assertEquals(trieString(utf8Options("abc", "ab", "a")), ""
        + "a\n"
        + " -> 2\n"
        + " b\n"
        + "  -> 1\n"
        + "  c -> 0\n");
    assertEquals(trieString(utf8Options("abcd", "abce", "abc", "abcf", "abcg")), ""
        + "abc\n"
        + "   -> 2\n"
        + "   d -> 0\n"
        + "   e -> 1\n");
  }

  private Options utf8Options(String... options) {
    ByteString[] byteStrings = new ByteString[options.length];
    for (int i = 0; i < options.length; i++) {
      byteStrings[i] = ByteString.encodeUtf8(options[i]);
    }
    return Options.of(byteStrings);
  }

  private void assertSelect(String data, int expected, Options options) {
    Buffer buffer = new Buffer().writeUtf8(data);
    long dataSize = buffer.size;
    int actual = buffer.select(options);

    assertEquals(actual, expected);
    if (expected == -1) {
      assertEquals(buffer.size, dataSize);
    } else {
      assertEquals(buffer.size + options.get(expected).size(), dataSize);
    }
  }

  private void assertSelect(String data, int expected, String... options) {
    assertSelect(data, expected, utf8Options(options));
  }

  private String trieString(Options options) {
    StringBuilder result = new StringBuilder();
    printTrieNode(result, options, 0, "");
    return result.toString();
  }

  private void printTrieNode(StringBuilder out, Options options, int offset, String indent) {
    if (options.trie[offset + 1] != -1) {
      // Print the prefix.
      out.append(indent + "-> " + options.trie[offset + 1] + "\n");
    }

    if (options.trie[offset] > 0) {
      // Print the select.
      int selectChoiceCount = options.trie[offset];
      for (int i = 0; i < selectChoiceCount; i++) {
        out.append(indent + (char) options.trie[offset + 2 + i]);
        printTrieResult(out, options, options.trie[offset + 2 + selectChoiceCount + i], indent + " ");
      }
    } else {
      // Print the scan.
      int scanByteCount = -1 * options.trie[offset];
      out.append(indent);
      for (int i = 0; i < scanByteCount; i++) {
        out.append((char) options.trie[offset + 2 + i]);
      }
      printTrieResult(out, options, options.trie[offset + 2 + scanByteCount], indent + TestUtil.repeat(' ', scanByteCount));
    }
  }

  private void printTrieResult(StringBuilder out, Options options, int result, String indent) {
    if (result >= 0) {
      out.append(" -> " + result + "\n");
    } else {
      out.append("\n");
      printTrieNode(out, options, -1 * result, indent);
    }
  }
}
