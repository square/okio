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

import java.io.IOException;
import okio.ByteString;
import okio.Utf8;

public final class ExploreCharsets {
  public void run() throws Exception {
    dumpStringData("Café \uD83C\uDF69"); // NFC: é is one code point.
    dumpStringData("Café \uD83C\uDF69"); // NFD: e is one code point, its accent is another.
  }

  public void dumpStringData(String s) throws IOException {
    System.out.println("                       " + s);
    System.out.println("        String.length: " + s.length());
    System.out.println("String.codePointCount: " + s.codePointCount(0, s.length()));
    System.out.println("            Utf8.size: " + Utf8.size(s));
    System.out.println("          UTF-8 bytes: " + ByteString.encodeUtf8(s).hex());
    System.out.println();
  }

  public static void main(String... args) throws Exception {
    new ExploreCharsets().run();
  }
}
