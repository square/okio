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
package okio.zipfilesystem;

import java.io.IOException;
import okio.FileSystem;
import okio.Path;
import org.junit.Before;
import org.junit.Test;

import static okio.zipfilesystem.ZipFileSystemTestKt.randomToken;
import static org.assertj.core.api.Assertions.assertThat;

public final class ZipFileSystemJavaTest {
  private FileSystem fileSystem = FileSystem.SYSTEM;
  private Path base = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(randomToken());

  @Before
  public void setUp() throws Exception {
    fileSystem.createDirectory(base);
  }

  @Test
  public void zipFileSystemApi() throws IOException {
    Path zipPath = new ZipBuilder(base)
        .addEntry("hello.txt", "Hello World")
        .build();
    ZipFileSystem zipFileSystem = ZipFileSystem.open(fileSystem, zipPath);

    String content = zipFileSystem.read(Path.get("hello.txt"), source -> {
      try {
        return source.readUtf8();
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    });
    assertThat(content).isEqualTo("Hello World");
  }
}
