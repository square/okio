/*
 * Copyright (C) 2018 Square, Inc. and others.
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
package com.squareup.okio.benchmarks;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.RunnerException;

@Fork(1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class Utf8Benchmark {
  private static final Charset utf8 = StandardCharsets.UTF_8;
  private static final Map<String, String> strings = new HashMap<>();

  static {
    strings.put(
        "ascii",
        "Um, I'll tell you the problem with the scientific power that you're using here, "
            + "it didn't require any discipline to attain it. You read what others had done and you "
            + "took the next step. You didn't earn the knowledge for yourselves, so you don't take any "
            + "responsibility for it. You stood on the shoulders of geniuses to accomplish something "
            + "as fast as you could, and before you even knew what you had, you patented it, and "
            + "packaged it, and slapped it on a plastic lunchbox, and now you're selling it, you wanna "
            + "sell it.");

    strings.put(
        "utf8",
        "Սｍ, I'll 𝓽𝖾ll ᶌօ𝘂 ᴛℎ℮ 𝜚𝕣०ｂl𝖾ｍ ｗі𝕥𝒽 𝘵𝘩𝐞 𝓼𝙘𝐢𝔢𝓷𝗍𝜄𝚏𝑖ｃ 𝛠𝝾ｗ𝚎𝑟 𝕥ｈ⍺𝞃 𝛄𝓸𝘂'𝒓𝗲 υ𝖘𝓲𝗇ɡ 𝕙𝚎𝑟ｅ, "
            + "𝛊𝓽 ⅆ𝕚𝐝𝝿'𝗍 𝔯𝙚𝙦ᴜ𝜾𝒓𝘦 𝔞𝘯𝐲 ԁ𝜄𝑠𝚌ι𝘱lι𝒏ｅ 𝑡𝜎 𝕒𝚝𝖙𝓪і𝞹 𝔦𝚝. 𝒀ο𝗎 𝔯𝑒⍺𝖉 ｗ𝐡𝝰𝔱 𝞂𝞽һ𝓮𝓇ƽ հ𝖺𝖉 ⅾ𝛐𝝅ⅇ 𝝰πԁ 𝔂ᴑᴜ 𝓉ﮨ၀𝚔 "
            + "т𝒽𝑒 𝗇𝕖ⅹ𝚝 𝔰𝒕е𝓅. 𝘠ⲟ𝖚 𝖉ⅰԁ𝝕'τ 𝙚𝚊ｒ𝞹 𝘵Ꮒ𝖾 𝝒𝐧هｗl𝑒𝖉ƍ𝙚 𝓯૦ｒ 𝔂𝞼𝒖𝕣𝑠𝕖l𝙫𝖊𝓼, 𐑈о ｙ𝘰𝒖 ⅆە𝗇'ｔ 𝜏α𝒌𝕖 𝛂𝟉ℽ "
            + "𝐫ⅇ𝗌ⲣ๐ϖ𝖘ꙇᖯ𝓲l𝓲𝒕𝘆 𝐟𝞼𝘳 𝚤𝑡. 𝛶𝛔𝔲 ｓ𝕥σσ𝐝 ﮩ𝕟 𝒕𝗁𝔢 𝘴𝐡𝜎ᴜlⅾ𝓮𝔯𝚜 𝛐𝙛 ᶃ𝚎ᴨᎥս𝚜𝘦𝓈 𝓽𝞸 ａ𝒄𝚌𝞸ｍρl𝛊ꜱ𝐡 𝓈𝚘ｍ𝚎𝞃𝔥⍳𝞹𝔤 𝐚𝗌 𝖋ａ𝐬𝒕 "
            + "αｓ γ𝛐𝕦 𝔠ﻫ𝛖lԁ, 𝚊π𝑑 Ь𝑒𝙛૦𝓇𝘦 𝓎٥𝖚 ⅇｖℯ𝝅 𝜅ո𝒆ｗ ｗ𝗵𝒂𝘁 ᶌ੦𝗎 ｈ𝐚𝗱, 𝜸ﮨ𝒖 𝓹𝝰𝔱𝖾𝗇𝓽𝔢ⅆ і𝕥, 𝚊𝜛𝓭 𝓹𝖺ⅽϰ𝘢ℊеᏧ 𝑖𝞃, "
            + "𝐚𝛑ꓒ 𝙨l𝔞р𝘱𝔢𝓭 ɩ𝗍 ہ𝛑 𝕒 ｐl𝛂ѕᴛ𝗂𝐜 l𝞄ℼ𝔠𝒽𝑏ﮪ⨯, 𝔞ϖ𝒹 ｎ𝛔ｗ 𝛾𝐨𝞄'𝗿𝔢 ꜱ℮ll𝙞ｎɡ ɩ𝘁, 𝙮𝕠𝛖 ｗ𝑎ℼ𝚗𝛂 𝕤𝓮ll 𝙞𝓉.");

    // The first 't' is actually a '𝓽'
    strings.put(
        "sparse",
        "Um, I'll 𝓽ell you the problem with the scientific power that you're using here, "
            + "it didn't require any discipline to attain it. You read what others had done and you "
            + "took the next step. You didn't earn the knowledge for yourselves, so you don't take any "
            + "responsibility for it. You stood on the shoulders of geniuses to accomplish something "
            + "as fast as you could, and before you even knew what you had, you patented it, and "
            + "packaged it, and slapped it on a plastic lunchbox, and now you're selling it, you wanna "
            + "sell it.");

    strings.put("2bytes", "\u0080\u07ff");

    strings.put("3bytes", "\u0800\ud7ff\ue000\uffff");

    strings.put("4bytes", "\ud835\udeca");

    // high surrogate, 'a', low surrogate, and 'a'
    strings.put("bad", "\ud800\u0061\udc00\u0061");
  }

  @Param({"20", "2000", "200000"})
  int length;

  @Param({"ascii", "utf8", "sparse", "2bytes", "3bytes", "4bytes", "bad"})
  String encoding;

  String encode;
  byte[] decodeArray;

  @Setup
  public void setup() {
    String part = strings.get(encoding);

    // Make all the strings the same length for comparison
    StringBuilder builder = new StringBuilder(length + 1_000);
    while (builder.length() < length) {
      builder.append(part);
    }
    builder.setLength(length);

    // Prepare a string and byte array for encoding and decoding
    encode = builder.toString();
    decodeArray = encode.getBytes(utf8);
  }

  @Benchmark
  public byte[] stringToBytesOkio() {
    return BenchmarkUtils.encodeUtf8(encode);
  }

  @Benchmark
  public byte[] stringToBytesJava() {
    return encode.getBytes(utf8);
  }

  @Benchmark
  public String bytesToStringOkio() {
    // For ASCII only decoding, this will never be faster than Java. Because
    // Java can trust the decoded char array and it will be the correct size for
    // ASCII, it is able to avoid the extra defensive copy Okio is forced to
    // make because it doesn't have access to String internals.
    return BenchmarkUtils.decodeUtf8(decodeArray);
  }

  @Benchmark
  public String bytesToStringJava() {
    return new String(decodeArray, utf8);
  }

  public static void main(String[] args) throws IOException, RunnerException {
    Main.main(new String[] {Utf8Benchmark.class.getName()});
  }
}
