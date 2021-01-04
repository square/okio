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
import java.util.ArrayList;
import java.util.List;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public final class FileSystemJavaTest {
  @Test
  public void pathApi() {
    Path path = Path.get("/home/jesse/todo.txt");

    assertThat(Path.get("/home/jesse").resolve("todo.txt")).isEqualTo(path);
    assertThat(Path.get("/home/jesse/todo.txt", "/")).isEqualTo(path);
    assertThat(path.isAbsolute()).isTrue();
    assertThat(path.isRelative()).isFalse();
    assertThat(path.isRoot()).isFalse();
    assertThat(path.name()).isEqualTo("todo.txt");
    assertThat(path.nameBytes()).isEqualTo(ByteString.encodeUtf8("todo.txt"));
    assertThat(path.parent()).isEqualTo(Path.get("/home/jesse"));
    assertThat(path.volumeLetter()).isNull();
  }

  @Test
  public void fileSystemApi() throws IOException {
    assertThat(FileSystem.SYSTEM.metadata(FileSystem.SYSTEM_TEMPORARY_DIRECTORY)).isNotNull();
  }

  @Test
  public void fakeFileSystemApi() {
    FakeFileSystem fakeFileSystem = new FakeFileSystem();
    assertThat(fakeFileSystem.clock).isNotNull();
    assertThat(fakeFileSystem.allPaths()).isEmpty();
    assertThat(fakeFileSystem.openPaths()).isEmpty();
    fakeFileSystem.checkNoOpenFiles();
  }

  @Test
  public void forwardingFileSystemApi() throws IOException {
    FakeFileSystem fakeFileSystem = new FakeFileSystem();
    final List<String> log = new ArrayList<>();
    ForwardingFileSystem forwardingFileSystem = new ForwardingFileSystem(fakeFileSystem) {
      @Override public Path onPathParameter(Path path, String functionName, String parameterName) {
        log.add(functionName + "(" + parameterName + "=" + path + ")");
        return path;
      }
    };
    forwardingFileSystem.metadataOrNull(Path.get("/"));
    assertThat(log).containsExactly("metadataOrNull(path=/)");
  }
}
