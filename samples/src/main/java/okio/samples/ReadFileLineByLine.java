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
import okio.BufferedSource;
import okio.Okio;
import okio.Source;

public final class ReadFileLineByLine {
  public void run() throws Exception {
    readLines(new File("README.md"));
  }

  public void readLines(File file) throws IOException {
    try (Source fileSource = Okio.source(file);
         BufferedSource bufferedFileSource = Okio.buffer(fileSource)) {

      while (true) {
        String line = bufferedFileSource.readUtf8Line();
        if (line == null) break;

        if (line.contains("square")) {
          System.out.println(line);
        }
      }

    }
  }

  public static void main(String... args) throws Exception {
    new ReadFileLineByLine().run();
  }
}
