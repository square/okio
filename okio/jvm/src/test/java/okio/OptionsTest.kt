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

import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.test.fail

class OptionsTest {
  /** Confirm that options prefers the first-listed option, not the longest or shortest one. */
  @Test fun optionOrderTakesPrecedence() {
    assertSelect("abcdefg", 0, "abc", "abcdef")
    assertSelect("abcdefg", 0, "abcdef", "abc")
  }

  @Test fun simpleOptionsTrie() {
    assertThat(utf8Options("hotdog", "hoth", "hot").trieString()).isEqualTo("""
        |hot
        |   -> 2
        |   d
        |    og -> 0
        |   h -> 1
        |""".trimMargin())
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
        "pingInterval")
    assertThat(options.trieString()).isEqualTo("""
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
        |""".trimMargin())
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
    try {
      utf8Options()
      fail()
    } catch (expected: IllegalArgumentException) {
    }
  }

  @Test fun emptyStringInOptionsTrie() {
    try {
      utf8Options("")
      fail()
    } catch (expected: IllegalArgumentException) {
    }
    try {
      utf8Options("abc", "")
      fail()
    } catch (expected: IllegalArgumentException) {
    }
  }

  @Test fun multipleIdenticalValues() {
    try {
      utf8Options("abc", "abc")
      fail()
    } catch (expected: IllegalArgumentException) {
      assertThat(expected).hasMessage("duplicate option: [text=abc]")
    }
  }

  @Test fun prefixesAreStripped() {
    val options = utf8Options("abcA", "abc", "abcB")
    assertThat(options.trieString()).isEqualTo("""
        |abc
        |   -> 1
        |   A -> 0
        |""".trimMargin())
    assertSelect("abc", 1, options)
    assertSelect("abcA", 0, options)
    assertSelect("abcB", 1, options)
    assertSelect("abcC", 1, options)
    assertSelect("ab", -1, options)
  }

  @Test fun multiplePrefixesAreStripped() {
    assertThat(utf8Options("a", "ab", "abc", "abcd", "abcde").trieString()).isEqualTo("""
        |a -> 0
        |""".trimMargin())
    assertThat(utf8Options("abc", "a", "ab", "abe", "abcd", "abcf").trieString()).isEqualTo("""
        |a
        | -> 1
        | bc -> 0
        |""".trimMargin())
    assertThat(utf8Options("abc", "ab", "a").trieString()).isEqualTo("""
        |a
        | -> 2
        | b
        |  -> 1
        |  c -> 0
        |""".trimMargin())
    assertThat(utf8Options("abcd", "abce", "abc", "abcf", "abcg").trieString()).isEqualTo("""
        |abc
        |   -> 2
        |   d -> 0
        |   e -> 1
        |""".trimMargin())
  }

  private fun utf8Options(vararg options: String): Options {
    return Options.of(*options.map { it.encodeUtf8() }.toTypedArray())
  }

  private fun assertSelect(data: String, expected: Int, options: Options) {
    val buffer = Buffer().writeUtf8(data)
    val dataSize = buffer.size
    val actual = buffer.select(options)

    assertThat(actual).isEqualTo(expected)
    if (expected == -1) {
      assertThat(buffer.size).isEqualTo(dataSize)
    } else {
      assertThat(buffer.size + options[expected].size).isEqualTo(dataSize)
    }
  }

  private fun assertSelect(data: String, expected: Int, vararg options: String) {
    assertSelect(data, expected, utf8Options(*options))
  }

  private fun Options.trieString() : String {
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