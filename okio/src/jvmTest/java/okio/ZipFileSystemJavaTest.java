/*
 * Copyright (C) 2020 Square, Inc.
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
import org.junit.Before;
import org.junit.Test;

import static okio.TestingCommonKt.randomToken;
import static org.assertj.core.api.Assertions.assertThat;

public final class ZipFileSystemJavaTest {
  private FileSystem fileSystem = FileSystem.SYSTEM;
  private Path base = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(randomToken(16));

  @Before
  public void setUp() throws Exception {
    fileSystem.createDirectory(base);
  }

  @Test
  public void zipFileSystemApi() throws IOException {
    Path zipPath = new ZipBuilder(base)
        .addEntry("hello.txt", "Hello World")
        .build();
    FileSystem zipFileSystem = Okio.openZip(fileSystem, zipPath);

    try (BufferedSource source = Okio.buffer(zipFileSystem.source(Path.get("hello.txt", false)))) {
      String content = source.readUtf8();
      assertThat(content).isEqualTo("Hello World");
    }
  }
}
