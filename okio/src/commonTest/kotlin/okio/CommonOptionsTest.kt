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
package okio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail
import okio.ByteString.Companion.encodeUtf8

class CommonOptionsTest {
  /** Confirm that options prefers the first-listed option, not the longest or shortest one. */
  @Test fun optionOrderTakesPrecedence() {
    assertSelect("abcdefg", 0, "abc", "abcdef")
    assertSelect("abcdefg", 0, "abcdef", "abc")
  }

  @Test fun simpleOptionsTrie() {
    assertEquals(
      utf8Options("hotdog", "hoth", "hot").trieString(),
      """
      |hot
      |   -> 2
      |   d
      |    og -> 0
      |   h -> 1
      |
      """.trimMargin(),
    )
  }

  @Test fun realisticOptionsTrie() {
    // These are the fields of OkHttpClient in 3.10.
    val options = utf8Options(
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
      "pingInterval",
    )
    assertEquals(
      options.trieString(),
      """
      |a
      | uthenticator -> 17
      |c
      | a
      |  che -> 9
      | e
      |  rtificate
      |           C
      |            hainCleaner -> 13
      |           P
      |            inner -> 15
      | o
      |  n
      |   nect
      |       T
      |        imeout -> 23
      |       i
      |        on
      |          P
      |           ool -> 18
      |          S
      |           pecs -> 3
      |  o
      |   kieJar -> 8
      |d
      | i
      |  spatcher -> 0
      | n
      |  s -> 19
      |e
      | ventListenerFactory -> 6
      |f
      | ollow
      |      R
      |       edirects -> 21
      |      S
      |       slRedirects -> 20
      |h
      | ostnameVerifier -> 14
      |i
      | nter
      |     c
      |      eptors -> 4
      |     n
      |      alCache -> 10
      |n
      | etworkInterceptors -> 5
      |p
      | i
      |  ngInterval -> 26
      | r
      |  o
      |   t
      |    ocols -> 2
      |   x
      |    y -> 1
      |r
      | e
      |  a
      |   dTimeout -> 24
      |  t
      |   ryOnConnectionFailure -> 22
      |s
      | o
      |  cketFactory -> 11
      | s
      |  lSocketFactory -> 12
      |w
      | riteTimeout -> 25
      |
      """.trimMargin(),
    )
    assertSelect("", -1, options)
    assertSelect("a", -1, options)
    assertSelect("eventListenerFactor", -1, options)
    assertSelect("dnst", 19, options)
    assertSelect("proxyproxy", 1, options)
    assertSelect("prox", -1, options)

    assertSelect("dispatcher", 0, options)
    assertSelect("proxy", 1, options)
    assertSelect("protocols", 2, options)
    assertSelect("connectionSpecs", 3, options)
    assertSelect("interceptors", 4, options)
    assertSelect("networkInterceptors", 5, options)
    assertSelect("eventListenerFactory", 6, options)
    assertSelect("proxySelector", 1, options) // 'proxy' is a prefix.
    assertSelect("cookieJar", 8, options)
    assertSelect("cache", 9, options)
    assertSelect("internalCache", 10, options)
    assertSelect("socketFactory", 11, options)
    assertSelect("sslSocketFactory", 12, options)
    assertSelect("certificateChainCleaner", 13, options)
    assertSelect("hostnameVerifier", 14, options)
    assertSelect("certificatePinner", 15, options)
    assertSelect("proxyAuthenticator", 1, options) // 'proxy' is a prefix.
    assertSelect("authenticator", 17, options)
    assertSelect("connectionPool", 18, options)
    assertSelect("dns", 19, options)
    assertSelect("followSslRedirects", 20, options)
    assertSelect("followRedirects", 21, options)
    assertSelect("retryOnConnectionFailure", 22, options)
    assertSelect("connectTimeout", 23, options)
    assertSelect("readTimeout", 24, options)
    assertSelect("writeTimeout", 25, options)
    assertSelect("pingInterval", 26, options)
  }

  @Test fun emptyOptions() {
    val options = utf8Options()
    assertSelect("", -1, options)
    assertSelect("a", -1, options)
    assertSelect("abc", -1, options)
  }

  @Test fun emptyStringInOptionsTrie() {
    assertFailsWith<IllegalArgumentException> {
      utf8Options("")
    }
    assertFailsWith<IllegalArgumentException> {
      utf8Options("abc", "")
    }
  }

  @Test fun multipleIdenticalValues() {
    try {
      utf8Options("abc", "abc")
      fail()
    } catch (expected: IllegalArgumentException) {
      assertEquals(expected.message, "duplicate option: [text=abc]")
    }
  }

  @Test fun prefixesAreStripped() {
    val options = utf8Options("abcA", "abc", "abcB")
    assertEquals(
      options.trieString(),
      """
      |abc
      |   -> 1
      |   A -> 0
      |
      """.trimMargin(),
    )
    assertSelect("abc", 1, options)
    assertSelect("abcA", 0, options)
    assertSelect("abcB", 1, options)
    assertSelect("abcC", 1, options)
    assertSelect("ab", -1, options)
  }

  @Test fun multiplePrefixesAreStripped() {
    assertEquals(
      utf8Options("a", "ab", "abc", "abcd", "abcde").trieString(),
      """
      |a -> 0
      |
      """.trimMargin(),
    )
    assertEquals(
      utf8Options("abc", "a", "ab", "abe", "abcd", "abcf").trieString(),
      """
      |a
      | -> 1
      | bc -> 0
      |
      """.trimMargin(),
    )
    assertEquals(
      utf8Options("abc", "ab", "a").trieString(),
      """
      |a
      | -> 2
      | b
      |  -> 1
      |  c -> 0
      |
      """.trimMargin(),
    )
    assertEquals(
      utf8Options("abcd", "abce", "abc", "abcf", "abcg").trieString(),
      """
      |abc
      |   -> 2
      |   d -> 0
      |   e -> 1
      |
      """.trimMargin(),
    )
  }

  @Test fun scan() {
    val options = utf8Options("abc")
    assertSelect("abcde", 0, options)
  }

  @Test fun scanReturnsPrefix() {
    val options = utf8Options("abcdefg", "ab")
    assertSelect("ab", 1, options)
    assertSelect("abcd", 1, options)
    assertSelect("abcdefg", 0, options)
    assertSelect("abcdefghi", 0, options)
    assertSelect("abcdhi", 1, options)
  }

  @Test fun select() {
    val options = utf8Options("a", "b", "c")
    assertSelect("a", 0, options)
    assertSelect("b", 1, options)
    assertSelect("c", 2, options)
    assertSelect("d", -1, options)
    assertSelect("aa", 0, options)
    assertSelect("bb", 1, options)
    assertSelect("cc", 2, options)
    assertSelect("dd", -1, options)
  }

  @Test fun selectSelect() {
    val options = utf8Options("aa", "ab", "ba", "bb")
    assertSelect("a", -1, options)
    assertSelect("b", -1, options)
    assertSelect("c", -1, options)
    assertSelect("aa", 0, options)
    assertSelect("ab", 1, options)
    assertSelect("ac", -1, options)
    assertSelect("ba", 2, options)
    assertSelect("bb", 3, options)
    assertSelect("bc", -1, options)
    assertSelect("ca", -1, options)
    assertSelect("cb", -1, options)
    assertSelect("cc", -1, options)
  }

  @Test fun selectScan() {
    val options = utf8Options("abcd", "defg")
    assertSelect("a", -1, options)
    assertSelect("d", -1, options)
    assertSelect("h", -1, options)
    assertSelect("ab", -1, options)
    assertSelect("ae", -1, options)
    assertSelect("de", -1, options)
    assertSelect("db", -1, options)
    assertSelect("hi", -1, options)
    assertSelect("abcd", 0, options)
    assertSelect("aefg", -1, options)
    assertSelect("defg", 1, options)
    assertSelect("dbcd", -1, options)
    assertSelect("hijk", -1, options)
    assertSelect("abcdh", 0, options)
    assertSelect("defgh", 1, options)
    assertSelect("hijkl", -1, options)
  }

  @Test fun scanSelect() {
    val options = utf8Options("abcd", "abce")
    assertSelect("a", -1, options)
    assertSelect("f", -1, options)
    assertSelect("abc", -1, options)
    assertSelect("abf", -1, options)
    assertSelect("abcd", 0, options)
    assertSelect("abce", 1, options)
    assertSelect("abcf", -1, options)
    assertSelect("abcdf", 0, options)
    assertSelect("abcef", 1, options)
  }

  @Test fun scanSpansSegments() {
    val options = utf8Options("abcd")
    assertSelect(bufferWithSegments("a", "bcd"), 0, options)
    assertSelect(bufferWithSegments("a", "bcde"), 0, options)
    assertSelect(bufferWithSegments("ab", "cd"), 0, options)
    assertSelect(bufferWithSegments("ab", "cde"), 0, options)
    assertSelect(bufferWithSegments("abc", "d"), 0, options)
    assertSelect(bufferWithSegments("abc", "de"), 0, options)
    assertSelect(bufferWithSegments("abcd", "e"), 0, options)
    assertSelect(bufferWithSegments("a", "bce"), -1, options)
    assertSelect(bufferWithSegments("a", "bce"), -1, options)
    assertSelect(bufferWithSegments("ab", "ce"), -1, options)
    assertSelect(bufferWithSegments("ab", "ce"), -1, options)
    assertSelect(bufferWithSegments("abc", "e"), -1, options)
    assertSelect(bufferWithSegments("abc", "ef"), -1, options)
    assertSelect(bufferWithSegments("abce", "f"), -1, options)
  }

  @Test fun selectSpansSegments() {
    val options = utf8Options("aa", "ab", "ba", "bb")
    assertSelect(bufferWithSegments("a", "a"), 0, options)
    assertSelect(bufferWithSegments("a", "b"), 1, options)
    assertSelect(bufferWithSegments("a", "c"), -1, options)
    assertSelect(bufferWithSegments("b", "a"), 2, options)
    assertSelect(bufferWithSegments("b", "b"), 3, options)
    assertSelect(bufferWithSegments("b", "c"), -1, options)
    assertSelect(bufferWithSegments("c", "a"), -1, options)
    assertSelect(bufferWithSegments("c", "b"), -1, options)
    assertSelect(bufferWithSegments("c", "c"), -1, options)
    assertSelect(bufferWithSegments("a", "ad"), 0, options)
    assertSelect(bufferWithSegments("a", "bd"), 1, options)
    assertSelect(bufferWithSegments("a", "cd"), -1, options)
    assertSelect(bufferWithSegments("b", "ad"), 2, options)
    assertSelect(bufferWithSegments("b", "bd"), 3, options)
    assertSelect(bufferWithSegments("b", "cd"), -1, options)
    assertSelect(bufferWithSegments("c", "ad"), -1, options)
    assertSelect(bufferWithSegments("c", "bd"), -1, options)
    assertSelect(bufferWithSegments("c", "cd"), -1, options)
  }

  private fun utf8Options(vararg options: String): Options {
    return Options.of(*options.map { it.encodeUtf8() }.toTypedArray())
  }

  private fun assertSelect(data: String, expected: Int, options: Options) {
    assertSelect(Buffer().writeUtf8(data), expected, options)
  }

  private fun assertSelect(data: String, expected: Int, vararg options: String) {
    assertSelect(data, expected, utf8Options(*options))
  }

  private fun assertSelect(data: Buffer, expected: Int, options: Options) {
    val initialSize = data.size
    val actual = data.select(options)

    assertEquals(actual, expected)
    if (expected == -1) {
      assertEquals(data.size, initialSize)
    } else {
      assertEquals(data.size + options[expected].size, initialSize)
    }
  }

  private fun Options.trieString(): String {
    val result = StringBuilder()
    printTrieNode(result, 0)
    return result.toString()
  }

  private fun Options.printTrieNode(out: StringBuilder, offset: Int = 0, indent: String = "") {
    if (trie[offset + 1] != -1) {
      // Print the prefix.
      out.append("$indent-> ${trie[offset + 1]}\n")
    }

    if (trie[offset] > 0) {
      // Print the select.
      val selectChoiceCount = trie[offset]
      for (i in 0 until selectChoiceCount) {
        out.append("$indent${trie[offset + 2 + i].toChar()}")
        printTrieResult(out, trie[offset + 2 + selectChoiceCount + i], "$indent ")
      }
    } else {
      // Print the scan.
      val scanByteCount = -1 * trie[offset]
      out.append(indent)
      for (i in 0 until scanByteCount) {
        out.append(trie[offset + 2 + i].toChar())
      }
      printTrieResult(out, trie[offset + 2 + scanByteCount], "$indent${" ".repeat(scanByteCount)}")
    }
  }

  private fun Options.printTrieResult(out: StringBuilder, result: Int, indent: String) {
    if (result >= 0) {
      out.append(" -> $result\n")
    } else {
      out.append("\n")
      printTrieNode(out, -1 * result, indent)
    }
  }
}
