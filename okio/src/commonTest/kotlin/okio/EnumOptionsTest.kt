/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"),
;
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

class EnumOptionsTest {
  enum class ShorterFirst(override val byteString: ByteString) : EnumOption {
    Abc("abc".encodeUtf8()),
    Abcdef("abcdef".encodeUtf8()),
    ;
    companion object : EnumOptions<ShorterFirst>(entries)
  }
  enum class LongerFirst(override val byteString: ByteString) : EnumOption {
    Abcdef("abcdef".encodeUtf8()),
    Abc("abc".encodeUtf8()),
    ;
    companion object : EnumOptions<LongerFirst>(entries)
  }

  /** Confirm that options prefers the first-listed option, not the longest or shortest one. */
  @Test fun optionOrderTakesPrecedence() {
    assertSelect("abcdefg", ShorterFirst.Abc, ShorterFirst)
    assertSelect("abcdefg", LongerFirst.Abcdef, LongerFirst)
  }

  // These are the fields of OkHttpClient in 3.10.
  enum class OkHttpClientOption(val text: String) : EnumOption {
    Dispatcher("dispatcher"),
    Proxy("proxy"),
    Protocols("protocols"),
    ConnectionSpecs("connectionSpecs"),
    Interceptors("interceptors"),
    NetworkInterceptors("networkInterceptors"),
    EventListenerFactory("eventListenerFactory"),
    ProxySelector("proxySelector"),
    CookieJar("cookieJar"),
    Cache("cache"),
    InternalCache("internalCache"),
    SocketFactory("socketFactory"),
    SslSocketFactory("sslSocketFactory"),
    CertificateChainCleaner("certificateChainCleaner"),
    HostnameVerifier("hostnameVerifier"),
    CertificatePinner("certificatePinner"),
    ProxyAuthenticator("proxyAuthenticator"),
    Authenticator("authenticator"),
    ConnectionPool("connectionPool"),
    Dns("dns"),
    FollowSslRedirects("followSslRedirects"),
    FollowRedirects("followRedirects"),
    RetryOnConnectionFailure("retryOnConnectionFailure"),
    ConnectTimeout("connectTimeout"),
    ReadTimeout("readTimeout"),
    WriteTimeout("writeTimeout"),
    PingInterval("pingInterval"),
    ;
    override val byteString: ByteString = text.encodeUtf8()
    companion object : EnumOptions<OkHttpClientOption>(entries)
  }

  @Test fun realisticOptionsTrie() {
    assertSelect("", null, OkHttpClientOption)
    assertSelect("a", null, OkHttpClientOption)
    assertSelect("eventListenerFactor", null, OkHttpClientOption)
    assertSelect("dnst", OkHttpClientOption.Dns, OkHttpClientOption)
    assertSelect("proxyproxy", OkHttpClientOption.Proxy, OkHttpClientOption)
    assertSelect("prox", null, OkHttpClientOption)

    assertSelect("dispatcher", OkHttpClientOption.Dispatcher, OkHttpClientOption)
    assertSelect("proxy", OkHttpClientOption.Proxy, OkHttpClientOption)
    assertSelect("protocols", OkHttpClientOption.Protocols, OkHttpClientOption)
    assertSelect("connectionSpecs", OkHttpClientOption.ConnectionSpecs, OkHttpClientOption)
    assertSelect("interceptors", OkHttpClientOption.Interceptors, OkHttpClientOption)
    assertSelect("networkInterceptors", OkHttpClientOption.NetworkInterceptors, OkHttpClientOption)
    assertSelect("eventListenerFactory", OkHttpClientOption.EventListenerFactory, OkHttpClientOption)
    assertSelect("proxySelector", OkHttpClientOption.Proxy, OkHttpClientOption) // 'proxy' is a prefix.
    assertSelect("cookieJar", OkHttpClientOption.CookieJar, OkHttpClientOption)
    assertSelect("cache", OkHttpClientOption.Cache, OkHttpClientOption)
    assertSelect("internalCache", OkHttpClientOption.InternalCache, OkHttpClientOption)
    assertSelect("socketFactory", OkHttpClientOption.SocketFactory, OkHttpClientOption)
    assertSelect("sslSocketFactory", OkHttpClientOption.SslSocketFactory, OkHttpClientOption)
    assertSelect("certificateChainCleaner", OkHttpClientOption.CertificateChainCleaner, OkHttpClientOption)
    assertSelect("hostnameVerifier", OkHttpClientOption.HostnameVerifier, OkHttpClientOption)
    assertSelect("certificatePinner", OkHttpClientOption.CertificatePinner, OkHttpClientOption)
    assertSelect("proxyAuthenticator", OkHttpClientOption.Proxy, OkHttpClientOption) // 'proxy' is a prefix.
    assertSelect("authenticator", OkHttpClientOption.Authenticator, OkHttpClientOption)
    assertSelect("connectionPool", OkHttpClientOption.ConnectionPool, OkHttpClientOption)
    assertSelect("dns", OkHttpClientOption.Dns, OkHttpClientOption)
    assertSelect("followSslRedirects", OkHttpClientOption.FollowSslRedirects, OkHttpClientOption)
    assertSelect("followRedirects", OkHttpClientOption.FollowRedirects, OkHttpClientOption)
    assertSelect("retryOnConnectionFailure", OkHttpClientOption.RetryOnConnectionFailure, OkHttpClientOption)
    assertSelect("connectTimeout", OkHttpClientOption.ConnectTimeout, OkHttpClientOption)
    assertSelect("readTimeout", OkHttpClientOption.ReadTimeout, OkHttpClientOption)
    assertSelect("writeTimeout", OkHttpClientOption.WriteTimeout, OkHttpClientOption)
    assertSelect("pingInterval", OkHttpClientOption.PingInterval, OkHttpClientOption)
  }

  @Test fun emptyOptions() {
    val options = EnumOptions.of<Nothing>(emptyList())
    assertSelect("", null, options)
    assertSelect("a", null, options)
    assertSelect("abc", null, options)
  }

  enum class EmptyStringOption(override val byteString: ByteString) : EnumOption {
    A("a".encodeUtf8()),
    Empty("".encodeUtf8()),
  }

  @Test fun emptyStringInOptionsTrie() {
    assertFailsWith<IllegalArgumentException> {
      EnumOptions.of(listOf(EmptyStringOption.Empty))
    }
    assertFailsWith<IllegalArgumentException> {
      EnumOptions.of<EmptyStringOption>()
    }
  }

  enum class IdenticalOption(override val byteString: ByteString) : EnumOption {
    A("abc".encodeUtf8()),
    B("abc".encodeUtf8()),
  }

  @Test fun multipleIdenticalValues() {
    try {
      EnumOptions.of<IdenticalOption>()
      fail()
    } catch (expected: IllegalArgumentException) {
      assertEquals(expected.message, "duplicate option: [text=abc]")
    }
  }

  enum class PrefixedOption(override val byteString: ByteString) : EnumOption {
    A("abcA".encodeUtf8()),
    Main("abc".encodeUtf8()),
    B("abcB".encodeUtf8()),
    ;
    companion object : EnumOptions<PrefixedOption>(entries)
  }

  @Test fun prefixesAreStripped() {
    assertSelect("abc", PrefixedOption.Main, PrefixedOption)
    assertSelect("abcA", PrefixedOption.A, PrefixedOption)
    assertSelect("abcB", PrefixedOption.Main, PrefixedOption)
    assertSelect("abcC", PrefixedOption.Main, PrefixedOption)
    assertSelect("ab", null, PrefixedOption)
  }

  enum class AbcOption(override val byteString: ByteString) : EnumOption {
    Abc("abc".encodeUtf8()),
    ;
    companion object : EnumOptions<AbcOption>(entries)
  }

  @Test fun scan() {
    assertSelect("abcde", AbcOption.Abc, AbcOption)
  }

  enum class AbcdefgOption(override val byteString: ByteString) : EnumOption {
    Abcdefg("abcdefg".encodeUtf8()),
    Ab("ab".encodeUtf8()),
    ;
    companion object : EnumOptions<AbcdefgOption>(entries)
  }

  @Test fun scanReturnsPrefix() {
    assertSelect("ab", AbcdefgOption.Ab, AbcdefgOption)
    assertSelect("abcd", AbcdefgOption.Ab, AbcdefgOption)
    assertSelect("abcdefg", AbcdefgOption.Abcdefg, AbcdefgOption)
    assertSelect("abcdefghi", AbcdefgOption.Abcdefg, AbcdefgOption)
    assertSelect("abcdhi", AbcdefgOption.Ab, AbcdefgOption)
  }

  enum class ABCOption(override val byteString: ByteString) : EnumOption {
    A("a".encodeUtf8()),
    B("b".encodeUtf8()),
    C("c".encodeUtf8()),
    ;
    companion object : EnumOptions<ABCOption>(entries)
  }

  @Test fun select() {
    assertSelect("a", ABCOption.A, ABCOption)
    assertSelect("b", ABCOption.B, ABCOption)
    assertSelect("c", ABCOption.C, ABCOption)
    assertSelect("d", null, ABCOption)
    assertSelect("aa", ABCOption.A, ABCOption)
    assertSelect("bb", ABCOption.B, ABCOption)
    assertSelect("cc", ABCOption.C, ABCOption)
    assertSelect("dd", null, ABCOption)
  }

  enum class PairOption(override val byteString: ByteString) : EnumOption {
    Aa("aa".encodeUtf8()),
    Ab("ab".encodeUtf8()),
    Ba("ba".encodeUtf8()),
    Bb("bb".encodeUtf8()),
    ;
    companion object : EnumOptions<PairOption>(entries)
  }

  @Test fun selectSelect() {
    assertSelect("a", null, PairOption)
    assertSelect("b", null, PairOption)
    assertSelect("c", null, PairOption)
    assertSelect("aa", PairOption.Aa, PairOption)
    assertSelect("ab", PairOption.Ab, PairOption)
    assertSelect("ac", null, PairOption)
    assertSelect("ba", PairOption.Ba, PairOption)
    assertSelect("bb", PairOption.Bb, PairOption)
    assertSelect("bc", null, PairOption)
    assertSelect("ca", null, PairOption)
    assertSelect("cb", null, PairOption)
    assertSelect("cc", null, PairOption)
  }

  enum class DisjointOption(override val byteString: ByteString) : EnumOption {
    Abcd("abcd".encodeUtf8()),
    Defg("defg".encodeUtf8()),
    ;
    companion object : EnumOptions<DisjointOption>(entries)
  }

  @Test fun selectScan() {
    assertSelect("a", null, DisjointOption)
    assertSelect("d", null, DisjointOption)
    assertSelect("h", null, DisjointOption)
    assertSelect("ab", null, DisjointOption)
    assertSelect("ae", null, DisjointOption)
    assertSelect("de", null, DisjointOption)
    assertSelect("db", null, DisjointOption)
    assertSelect("hi", null, DisjointOption)
    assertSelect("abcd", DisjointOption.Abcd, DisjointOption)
    assertSelect("aefg", null, DisjointOption)
    assertSelect("defg", DisjointOption.Defg, DisjointOption)
    assertSelect("dbcd", null, DisjointOption)
    assertSelect("hijk", null, DisjointOption)
    assertSelect("abcdh", DisjointOption.Abcd, DisjointOption)
    assertSelect("defgh", DisjointOption.Defg, DisjointOption)
    assertSelect("hijkl", null, DisjointOption)
  }

  enum class OverlappingOption(override val byteString: ByteString) : EnumOption {
    Abcd("abcd".encodeUtf8()),
    Abce("abce".encodeUtf8()),
    ;
    companion object : EnumOptions<OverlappingOption>(entries)
  }

  @Test fun scanSelect() {
    assertSelect("a", null, OverlappingOption)
    assertSelect("f", null, OverlappingOption)
    assertSelect("abc", null, OverlappingOption)
    assertSelect("abf", null, OverlappingOption)
    assertSelect("abcd", OverlappingOption.Abcd, OverlappingOption)
    assertSelect("abce", OverlappingOption.Abce, OverlappingOption)
    assertSelect("abcf", null, OverlappingOption)
    assertSelect("abcdf", OverlappingOption.Abcd, OverlappingOption)
    assertSelect("abcef", OverlappingOption.Abce, OverlappingOption)
  }

  enum class AbcdOption(override val byteString: ByteString) : EnumOption {
    Abcd("abcd".encodeUtf8()),
    ;
    companion object : EnumOptions<AbcdOption>(entries)
  }

  @Test fun scanSpansSegments() {
    assertSelect(bufferWithSegments("a", "bcd"), AbcdOption.Abcd, AbcdOption)
    assertSelect(bufferWithSegments("a", "bcde"), AbcdOption.Abcd, AbcdOption)
    assertSelect(bufferWithSegments("ab", "cd"), AbcdOption.Abcd, AbcdOption)
    assertSelect(bufferWithSegments("ab", "cde"), AbcdOption.Abcd, AbcdOption)
    assertSelect(bufferWithSegments("abc", "d"), AbcdOption.Abcd, AbcdOption)
    assertSelect(bufferWithSegments("abc", "de"), AbcdOption.Abcd, AbcdOption)
    assertSelect(bufferWithSegments("abcd", "e"), AbcdOption.Abcd, AbcdOption)
    assertSelect(bufferWithSegments("a", "bce"), null, AbcdOption)
    assertSelect(bufferWithSegments("a", "bce"), null, AbcdOption)
    assertSelect(bufferWithSegments("ab", "ce"), null, AbcdOption)
    assertSelect(bufferWithSegments("ab", "ce"), null, AbcdOption)
    assertSelect(bufferWithSegments("abc", "e"), null, AbcdOption)
    assertSelect(bufferWithSegments("abc", "ef"), null, AbcdOption)
    assertSelect(bufferWithSegments("abce", "f"), null, AbcdOption)
  }

  @Test fun selectSpansSegments() {
    assertSelect(bufferWithSegments("a", "a"), PairOption.Aa, PairOption)
    assertSelect(bufferWithSegments("a", "b"), PairOption.Ab, PairOption)
    assertSelect(bufferWithSegments("a", "c"), null, PairOption)
    assertSelect(bufferWithSegments("b", "a"), PairOption.Ba, PairOption)
    assertSelect(bufferWithSegments("b", "b"), PairOption.Bb, PairOption)
    assertSelect(bufferWithSegments("b", "c"), null, PairOption)
    assertSelect(bufferWithSegments("c", "a"), null, PairOption)
    assertSelect(bufferWithSegments("c", "b"), null, PairOption)
    assertSelect(bufferWithSegments("c", "c"), null, PairOption)
    assertSelect(bufferWithSegments("a", "ad"), PairOption.Aa, PairOption)
    assertSelect(bufferWithSegments("a", "bd"), PairOption.Ab, PairOption)
    assertSelect(bufferWithSegments("a", "cd"), null, PairOption)
    assertSelect(bufferWithSegments("b", "ad"), PairOption.Ba, PairOption)
    assertSelect(bufferWithSegments("b", "bd"), PairOption.Bb, PairOption)
    assertSelect(bufferWithSegments("b", "cd"), null, PairOption)
    assertSelect(bufferWithSegments("c", "ad"), null, PairOption)
    assertSelect(bufferWithSegments("c", "bd"), null, PairOption)
    assertSelect(bufferWithSegments("c", "cd"), null, PairOption)
  }

  private fun <E : EnumOption> assertSelect(data: String, expected: E?, options: EnumOptions<E>) {
    assertSelect(Buffer().writeUtf8(data), expected, options)
  }

  private fun <E : EnumOption> assertSelect(data: Buffer, expected: E?, options: EnumOptions<E>) {
    val initialSize = data.size
    val actual = data.select(options)

    assertEquals(expected, actual)
    if (expected == null) {
      assertEquals(data.size, initialSize)
    } else {
      assertEquals(data.size + expected.byteString.size, initialSize)
    }
  }
}
