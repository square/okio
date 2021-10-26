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

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import kotlin.sequences.Sequence;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public final class FileSystemJavaTest {
  @Test
  public void pathApi() {
    Path path = Path.get("/home/jesse/todo.txt");

    assertThat(Path.get("/home/jesse").resolve("todo.txt")).isEqualTo(path);
    assertThat(Path.get("/home/jesse/todo.txt")).isEqualTo(path);
    assertThat(path.isAbsolute()).isTrue();
    assertThat(path.isRelative()).isFalse();
    assertThat(path.isRoot()).isFalse();
    assertThat(path.name()).isEqualTo("todo.txt");
    assertThat(path.nameBytes()).isEqualTo(ByteString.encodeUtf8("todo.txt"));
    assertThat(path.parent()).isEqualTo(Path.get("/home/jesse"));
    assertThat(path.volumeLetter()).isNull();
  }

  @Test
  public void directorySeparator() {
    assertThat(Path.DIRECTORY_SEPARATOR).isIn("/", "\\");
  }

  /** Like the same test in JvmTest, but this is using the Java APIs. */
  @Test
  public void javaIoFileToOkioPath() {
    String string = "/foo/bar/baz".replace("/", Path.DIRECTORY_SEPARATOR);
    File javaIoFile = new File(string);
    Path okioPath = Path.get(string);
    assertThat(Path.get(javaIoFile)).isEqualTo(okioPath);
    assertThat(okioPath.toFile()).isEqualTo(javaIoFile);
  }

  /** Like the same test in JvmTest, but this is using the Java APIs. */
  @Test
  public void nioPathToOkioPath() {
    String string = "/foo/bar/baz".replace("/", okio.Path.DIRECTORY_SEPARATOR);
    java.nio.file.Path nioPath = Paths.get(string);
    Path okioPath = Path.get(string);
    assertThat(Path.get(nioPath)).isEqualTo(okioPath);
    assertThat((Object) okioPath.toNioPath()).isEqualTo(nioPath);
  }

  // Just confirm these APIs exist; don't invoke them
  @SuppressWarnings("unused")
  public void fileSystemApi() throws IOException {
    FileSystem fileSystem = FileSystem.SYSTEM;
    Path pathA = Path.get("a.txt");
    Path pathB = Path.get("b.txt");
    Path canonicalized = fileSystem.canonicalize(pathA);
    FileMetadata metadata = fileSystem.metadata(pathA);
    FileMetadata metadataOrNull = fileSystem.metadataOrNull(pathA);
    boolean exists = fileSystem.exists(pathA);
    List<Path> list = fileSystem.list(pathA);
    List<Path> listOrNull = fileSystem.listOrNull(pathA);
    Sequence<Path> listRecursivelyBoolean = fileSystem.listRecursively(pathA, false);
    Sequence<Path> listRecursively = fileSystem.listRecursively(pathA);
    FileHandle openReadOnly = fileSystem.openReadOnly(pathA);
    FileHandle openReadOnlyBooleanBoolean = fileSystem.openReadWrite(pathA, false, false);
    FileHandle openReadWrite = fileSystem.openReadWrite(pathA);
    Source source = fileSystem.source(pathA);
    // Note that FileSystem.read() isn't available to Java callers.
    Sink sinkFalse = fileSystem.sink(pathA, false);
    Sink sink = fileSystem.sink(pathA);
    // Note that FileSystem.write() isn't available to Java callers.
    Sink appendingSinkBoolean = fileSystem.appendingSink(pathA, false);
    Sink appendingSink = fileSystem.appendingSink(pathA);
    fileSystem.createDirectory(pathA);
    fileSystem.createDirectories(pathA);
    fileSystem.atomicMove(pathA, pathB);
    fileSystem.copy(pathA, pathB);
    fileSystem.delete(pathA);
    fileSystem.deleteRecursively(pathA);
    fileSystem.createSymlink(pathA, pathB);
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
