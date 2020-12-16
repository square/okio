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
package okio.files

import okio.Path.Companion.toPath
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PathTest {
  @Test
  fun pathNames() {
    assertEquals(".", ".".toPath().name)
    assertEquals("", "/".toPath().name)
    assertEquals("..", "..".toPath().name)
    assertEquals("..", "../..".toPath().name)
    assertEquals(".gitignore", ".gitignore".toPath().name)
    assertEquals("Main.kt", "src/main/kotlin/Main.kt".toPath().name)
    assertEquals("foo.txt", "/home/jesse/foo.txt".toPath().name)
    assertEquals("jesse", "/home/jesse".toPath().name)
    assertEquals("passwd", "../../etc/passwd".toPath().name)
  }

  @Test
  fun basicPaths() {
    assertEquals("gradlew", "./gradlew".toPath().toString())
    assertEquals(".gitignore", "./.gitignore".toPath().toString())
    assertEquals("/home/jesse", "/home/jesse".toPath().toString())
    assertEquals("/home/jake", "/home/jesse/../jake".toPath().toString())
    assertEquals("../../etc/passwd", "../../etc/passwd".toPath().toString())
  }

  @Test
  fun parentOfAbsolutePath() {
    assertEquals("/home", "/home/jesse".toPath().parent?.toString())
    assertEquals("/", "/home".toPath().parent?.toString())
    assertNull("/".toPath().parent)
  }

  @Test
  fun parentOfRelativePath() {
    assertEquals("src/main", "src/main/java".toPath().parent?.toString())
    assertEquals("src", "src/main".toPath().parent?.toString())
    assertEquals(".", "src".toPath().parent?.toString())
    assertNull(".".toPath().parent)
  }

  @Test
  fun parentOfRelativePathWithSingleTraversal() {
    assertEquals("../src/main", "../src/main/java".toPath().parent?.toString())
    assertEquals("../src", "../src/main".toPath().parent?.toString())
    assertEquals("..", "../src".toPath().parent?.toString())
    assertNull("..".toPath().parent)
  }

  @Test
  fun parentOfRelativePathWithMultipleTraversal() {
    assertEquals("../../src/main", "../../src/main/java".toPath().parent?.toString())
    assertEquals("../../src", "../../src/main".toPath().parent?.toString())
    assertEquals("../..", "../../src".toPath().parent?.toString())
    assertNull("../..".toPath().parent)
  }

  @Test
  fun absolutePathTraversalWithDivOperator() {
    val root = "/".toPath()
    assertEquals("/home".toPath(), root / "home")
    assertEquals("/home/jesse".toPath(), root / "home" / "jesse")
    assertEquals("/home".toPath(), root / "home" / "jesse" / "..")
    assertEquals("/home/jake".toPath(), root / "home" / "jesse" / ".." / "jake")
  }

  @Test
  fun relativePathTraversalWithDivOperator() {
    val cwd = ".".toPath("/")
    assertEquals("home".toPath("/"), cwd / "home")
    assertEquals("home/jesse".toPath("/"), cwd / "home" / "jesse")
    assertEquals("home".toPath("/"), cwd / "home" / "jesse" / "..")
    assertEquals("home/jake".toPath("/"), cwd / "home" / "jesse" / ".." / "jake")
  }

  @Test
  fun relativePathTraversalWithDots() {
    val cwd = ".".toPath("/")
    assertEquals("..".toPath("/"), cwd / "..")
    assertEquals("../..".toPath("/"), cwd / ".." / "..")
    assertEquals("../../etc".toPath("/"), cwd / ".." / ".." / "etc")
    assertEquals("../../etc/passwd".toPath("/"), cwd / ".." / ".." / "etc" / "passwd")
  }

  @Test
  fun pathTraversalBaseIgnoredIfChildIsAnAbsolutePath() {
    assertEquals("/home".toPath(), "".toPath("/") / "/home")
    assertEquals("/home".toPath(), "relative".toPath("/") / "/home")
    assertEquals("/home".toPath(), "/base".toPath("/") / "/home")
    assertEquals("/home".toPath(), "/".toPath("/") / "/home")
  }

  @Test
  fun stringToAbsolutePath() {
    assertEquals("/", "/".toPath().toString())
    assertEquals("/a", "/a".toPath().toString())
    assertEquals("/a", "/a/".toPath().toString())
    assertEquals("/a/b/c", "/a/b/c".toPath().toString())
    assertEquals("/a/b/c", "/a/b/c/".toPath().toString())
  }

  @Test
  fun stringToAbsolutePathWithTraversal() {
    assertEquals("/", "/..".toPath().toString())
    assertEquals("/", "/../".toPath().toString())
    assertEquals("/", "/../..".toPath().toString())
    assertEquals("/", "/../../".toPath().toString())
  }

  @Test
  fun stringToAbsolutePathWithEmptySegments() {
    assertEquals("/", "//".toPath().toString())
    assertEquals("/a", "//a".toPath().toString())
    assertEquals("/a", "/a//".toPath().toString())
    assertEquals("/a", "//a//".toPath().toString())
    assertEquals("/a/b", "/a/b//".toPath().toString())
  }

  @Test
  fun stringToAbsolutePathWithDots() {
    assertEquals("/", "/./".toPath().toString())
    assertEquals("/a", "/./a".toPath().toString())
    assertEquals("/a", "/a/./".toPath().toString())
    assertEquals("/a", "/a//.".toPath().toString())
    assertEquals("/a", "/./a//".toPath().toString())
    assertEquals("/a", "/a/.".toPath().toString())
    assertEquals("/a", "//a/./".toPath().toString())
    assertEquals("/a", "//a/./.".toPath().toString())
    assertEquals("/a/b", "/a/./b/".toPath().toString())
  }

  @Test
  fun stringToRelativePath() {
    assertEquals(".", "".toPath().toString())
    assertEquals(".", ".".toPath().toString())
    assertEquals("a", "a/".toPath().toString())
    assertEquals("a/b", "a/b".toPath().toString())
    assertEquals("a/b", "a/b/".toPath().toString())
    assertEquals("a/b/c/d", "a/b/c/d".toPath().toString())
    assertEquals("a/b/c/d", "a/b/c/d/".toPath().toString())
  }

  @Test
  fun stringToRelativePathWithTraversal() {
    assertEquals("..", "..".toPath().toString())
    assertEquals("..", "../".toPath().toString())
    assertEquals(".", "a/..".toPath().toString())
    assertEquals(".", "a/../".toPath().toString())
    assertEquals("..", "a/../..".toPath().toString())
    assertEquals("..", "a/../../".toPath().toString())
    assertEquals("../..", "a/../../..".toPath().toString())
    assertEquals("../../b", "../../b".toPath().toString())
    assertEquals("../../b", "a/../../../b".toPath().toString())
    assertEquals("../../c", "a/../../../b/../c".toPath().toString())
  }

  @Test
  fun stringToRelativePathWithEmptySegments() {
    assertEquals("a", "a//".toPath().toString())
    assertEquals("a/b", "a//b".toPath().toString())
    assertEquals("a/b", "a/b//".toPath().toString())
    assertEquals("a/b", "a//b//".toPath().toString())
    assertEquals("a/b/c", "a/b/c//".toPath().toString())
  }

  @Test
  fun stringToRelativePathWithDots() {
    assertEquals(".", ".".toPath().toString())
    assertEquals(".", "./".toPath().toString())
    assertEquals(".", "././".toPath().toString())
    assertEquals(".", "././a/..".toPath().toString())
    assertEquals("a", "a/./".toPath().toString())
    assertEquals("a/b", "a/./b".toPath().toString())
    assertEquals("a/b", "a/b/./".toPath().toString())
    assertEquals("a/b", "a/b//.".toPath().toString())
    assertEquals("a/b", "a/./b//".toPath().toString())
    assertEquals("a/b", "a/b/.".toPath().toString())
    assertEquals("a/b", "a//b/./".toPath().toString())
    assertEquals("a/b", "a//b/./.".toPath().toString())
    assertEquals("a/b/c", "a/b/./c/".toPath().toString())
  }

  @Test
  fun windowsFullyQualifiedPath() {
    assertEquals("C:\\", "C:\\".toPath().toString())
    assertEquals("C:\\Windows\\notepad.exe", "C:\\Windows\\notepad.exe".toPath().toString())
  }

  @Test
  fun composingWindowsPath() {
    assertEquals("C:\\Windows\\notepad.exe".toPath(), "C:\\".toPath() / "Windows" / "notepad.exe")
  }

  @Test
  fun windowsPathTraversalUp() {
    assertEquals("C:\\z".toPath(), "C:\\x\\y\\..\\..\\..\\z".toPath())
    assertEquals("C:..\\z".toPath(), "C:x\\y\\..\\..\\..\\z".toPath())
  }

  @Test
  fun volumeLetter() {
    assertEquals('C', "C:\\Windows".toPath("\\").volumeLetter)
    assertEquals('C', "C:".toPath("\\").volumeLetter)
    assertEquals('z', "z:\\Windows".toPath("\\").volumeLetter)
    assertEquals('z', "z:".toPath("\\").volumeLetter)
    assertNull("\\\\server\\directory".toPath("\\").volumeLetter)
    assertNull("\\\\server\\directory".toPath("\\").volumeLetter)
    assertNull("\\Windows".toPath("\\").volumeLetter)
    assertNull("/Windows".toPath("\\").volumeLetter)
    assertNull("\\".toPath("\\").volumeLetter)
    assertNull("/".toPath("\\").volumeLetter)
    assertNull("C:".toPath("/").volumeLetter)
    assertNull("C:/Windows".toPath("/").volumeLetter)
  }

  @Test
  fun absoluteVolumeLetterPathParent() {
    assertEquals(null, "C:\\".toPath("\\").parent)
    assertEquals("C:\\".toPath("\\"), "C:\\Windows".toPath("\\").parent)
    assertEquals("C:\\Windows".toPath("\\"), "C:\\Windows\\system32".toPath("\\").parent)
  }

  @Test
  fun relativeVolumeLetterPathParent() {
    assertEquals(null, "C:".toPath("\\").parent)
    assertEquals("C:".toPath("\\"), "C:hello".toPath("\\").parent)
    assertEquals("C:hello".toPath("\\"), "C:hello\\world".toPath("\\").parent)
  }

  @Test
  fun windowsAbsolutePath() {
    assertEquals("\\Program Files", "\\Program Files".toPath().toString())
    assertEquals("\\Program Files\\Photoshop".toPath(), "\\Program Files".toPath() / "Photoshop")
  }

  /** If a volume is present in the relative path... */
  @Test
  fun windowsVolumeRelativePath() {
    assertEquals("c:notepad.exe".toPath("\\"), "A:\\DOOM".toPath() / "c:notepad.exe")
    assertEquals("c:notepad.exe".toPath("\\"), "C:\\Program Files".toPath() / "c:notepad.exe")
  }

  /**
   * > fopen accepts UNC paths and paths that involve mapped network drives as long as the system
   * > that executes the code has access to the share or mapped drive at the time of execution.
   * > When you construct paths for fopen, make sure that drives, paths, or network shares will be
   * > available in the execution environment. You can use either forward slashes (/) or backslashes
   * > (\) as the directory separators in a path.
   *
   * TODO(swankjesse): support \\?\ and \\.\ paths.
   */
  @Test @Ignore
  fun windowsQualifiedPath() {
    assertEquals("\\\\server\\share", "\\\\server\\share".toPath().toString())
    assertEquals("\\\\?\\directory", "\\\\?\\directory".toPath().toString())
    assertEquals("\\\\.\\COM56", "\\\\.\\COM56".toPath().toString())
    assertEquals("\\Global??", "\\Global??".toPath().toString())
    assertEquals("\\", "\\".toPath().toString())
  }
}
