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
package okio

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalFileSystem
class PathTest {
  @Test
  fun unixRoot() {
    val path = "/".toPath("/")
    assertEquals("/", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun unixAbsolutePath() {
    val path = "/home/jesse/todo.txt".toPath("/")
    assertEquals("/home/jesse/todo.txt", path.toString())
    assertEquals("/home/jesse".toPath("/"), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("todo.txt", path.name)
    assertTrue(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixRelativePath() {
    val path = "project/todo.txt".toPath("/")
    assertEquals("project/todo.txt", path.toString())
    assertEquals("project".toPath("/"), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("todo.txt", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixRelativePathWithDots() {
    val path = "../../project/todo.txt".toPath("/")
    assertEquals("../../project/todo.txt", path.toString())
    assertEquals("../../project".toPath("/"), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("todo.txt", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixRelativeSeriesOfDotDots() {
    val path = "../../..".toPath("/")
    assertEquals("../../..", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("..", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixAbsoluteSeriesOfDotDots() {
    val path = "/../../..".toPath("/")
    assertEquals("/", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun unixAbsoluteSingleDot() {
    val path = "/.".toPath("/")
    assertEquals("/", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun unixRelativeDoubleDots() {
    val path = "..".toPath("/")
    assertEquals("..", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("..", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixRelativeSingleDot() {
    val path = ".".toPath("/")
    assertEquals(".", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals(".", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsVolumeLetter() {
    val path = "C:\\".toPath("\\")
    assertEquals("C:\\", path.toString())
    assertNull(path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun windowsAbsolutePathWithVolumeLetter() {
    val path = "C:\\Windows\\notepad.exe".toPath("\\")
    assertEquals("C:\\Windows\\notepad.exe", path.toString())
    assertEquals("C:\\Windows".toPath("\\"), path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertTrue(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsAbsolutePath() {
    val path = "\\".toPath("\\")
    assertEquals("\\", path.toString())
    assertEquals(null, path.parent)
    assertNull(path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun windowsAbsolutePathWithoutVolumeLetter() {
    val path = "\\Windows\\notepad.exe".toPath("\\")
    assertEquals("\\Windows\\notepad.exe", path.toString())
    assertEquals("\\Windows".toPath("\\"), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertTrue(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsRelativePathWithVolumeLetter() {
    val path = "C:Windows\\notepad.exe".toPath("\\")
    assertEquals("C:Windows\\notepad.exe", path.toString())
    assertEquals("C:Windows".toPath("\\"), path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsVolumeLetterRelative() {
    val path = "C:".toPath("\\")
    assertEquals("C:", path.toString())
    assertNull(path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsRelativePath() {
    val path = "Windows\\notepad.exe".toPath("\\")
    assertEquals("Windows\\notepad.exe", path.toString())
    assertEquals("Windows".toPath("\\"), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsUncServer() {
    val path = "\\\\server".toPath("\\")
    assertEquals("\\\\server", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("server", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun windowsUncAbsolutePath() {
    val path = "\\\\server\\project\\notes.txt".toPath("\\")
    assertEquals("\\\\server\\project\\notes.txt", path.toString())
    assertEquals("\\\\server\\project".toPath("\\"), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("notes.txt", path.name)
    assertTrue(path.isAbsolute)
    assertFalse(path.isRoot)
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
  fun composingWindowsPath() {
    assertEquals("C:\\Windows\\notepad.exe".toPath(), "C:\\".toPath() / "Windows" / "notepad.exe")
  }

  @Test
  fun windowsResolveAbsolutePath() {
    assertEquals("\\Users".toPath(), "C:\\Windows".toPath() / "\\Users")
  }

  @Test
  fun windowsPathTraversalUp() {
    assertEquals("C:\\z".toPath(), "C:\\x\\y\\..\\..\\..\\z".toPath())
    assertEquals("C:..\\z".toPath(), "C:x\\y\\..\\..\\..\\z".toPath())
  }
}
