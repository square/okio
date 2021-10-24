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
import java.util.Map;
import okio.BufferedSink;
import okio.FileSystem;
import okio.Okio;
import okio.Path;
import okio.Sink;

public final class WriteFile {
  public void run() throws Exception {
    writeEnv(Path.get("env.txt"));
  }

  public void writeEnv(Path path) throws IOException {
    try (Sink fileSink = FileSystem.SYSTEM.sink(path);
         BufferedSink bufferedSink = Okio.buffer(fileSink)) {

      for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
        bufferedSink.writeUtf8(entry.getKey());
        bufferedSink.writeUtf8("=");
        bufferedSink.writeUtf8(entry.getValue());
        bufferedSink.writeUtf8("\n");
      }

    }
  }

  public static void main(String... args) throws Exception {
    new WriteFile().run();
  }
}
