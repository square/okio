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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath

@ExperimentalFileSystem
class PathTest {
  @Test
  fun unixRoot() {
    val path = "/".toPath()
    assertEquals(path, path.root)
    assertEquals(listOf(), path.segments)
    assertEquals("/", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun unixAbsolutePath() {
    val path = "/home/jesse/todo.txt".toPath()
    assertEquals("/".toPath(), path.root)
    assertEquals(listOf("home", "jesse", "todo.txt"), path.segments)
    assertEquals("/home/jesse/todo.txt", path.toString())
    assertEquals("/home/jesse".toPath(), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("todo.txt", path.name)
    assertTrue(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixRelativePath() {
    val path = "project/todo.txt".toPath()
    assertNull(path.root)
    assertEquals(listOf("project", "todo.txt"), path.segments)
    assertEquals("project/todo.txt", path.toString())
    assertEquals("project".toPath(), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("todo.txt", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixRelativePathWithDots() {
    val path = "../../project/todo.txt".toPath()
    assertNull(path.root)
    assertEquals(listOf("..", "..", "project", "todo.txt"), path.segments)
    assertEquals("../../project/todo.txt", path.toString())
    assertEquals("../../project".toPath(), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("todo.txt", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixRelativeSeriesOfDotDots() {
    val path = "../../..".toPath()
    assertNull(path.root)
    assertEquals(listOf("..", "..", ".."), path.segments)
    assertEquals("../../..", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("..", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixAbsoluteSeriesOfDotDots() {
    val path = "/../../..".toPath()
    assertEquals("/".toPath(), path.root)
    assertEquals(listOf(), path.segments)
    assertEquals("/", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun unixAbsoluteSingleDot() {
    val path = "/.".toPath()
    assertEquals("/".toPath(), path.root)
    assertEquals(listOf(), path.segments)
    assertEquals("/", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun unixRelativeDoubleDots() {
    val path = "..".toPath()
    assertNull(path.root)
    assertEquals(listOf(".."), path.segments)
    assertEquals("..", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("..", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixRelativeSingleDot() {
    val path = ".".toPath()
    assertNull(path.root)
    assertEquals(listOf("."), path.segments)
    assertEquals(".", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals(".", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsVolumeLetter() {
    val path = "C:\\".toPath()
    assertEquals("C:\\".toPath(), path.root)
    assertEquals(listOf(), path.segments)
    assertEquals("C:\\", path.toString())
    assertNull(path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun windowsAbsolutePathWithVolumeLetter() {
    val path = "C:\\Windows\\notepad.exe".toPath()
    assertEquals("C:\\".toPath(), path.root)
    assertEquals(listOf("Windows", "notepad.exe"), path.segments)
    assertEquals("C:\\Windows\\notepad.exe", path.toString())
    assertEquals("C:\\Windows".toPath(), path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertTrue(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsAbsolutePath() {
    val path = "\\".toPath()
    assertEquals("\\".toPath(), path.root)
    assertEquals(listOf(), path.segments)
    assertEquals("\\", path.toString())
    assertEquals(null, path.parent)
    assertNull(path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun windowsAbsolutePathWithoutVolumeLetter() {
    val path = "\\Windows\\notepad.exe".toPath()
    assertEquals("\\".toPath(), path.root)
    assertEquals(listOf("Windows", "notepad.exe"), path.segments)
    assertEquals("\\Windows\\notepad.exe", path.toString())
    assertEquals("\\Windows".toPath(), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertTrue(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsRelativePathWithVolumeLetter() {
    val path = "C:Windows\\notepad.exe".toPath()
    assertNull(path.root)
    assertEquals(listOf("C:Windows", "notepad.exe"), path.segments)
    assertEquals("C:Windows\\notepad.exe", path.toString())
    assertEquals("C:Windows".toPath(), path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsVolumeLetterRelative() {
    val path = "C:".toPath()
    assertNull(path.root)
    assertEquals(listOf("C:"), path.segments)
    assertEquals("C:", path.toString())
    assertNull(path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsRelativePath() {
    val path = "Windows\\notepad.exe".toPath()
    assertNull(path.root)
    assertEquals(listOf("Windows", "notepad.exe"), path.segments)
    assertEquals("Windows\\notepad.exe", path.toString())
    assertEquals("Windows".toPath(), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsUncServer() {
    val path = "\\\\server".toPath()
    assertEquals("\\\\server".toPath(), path.root)
    assertEquals(listOf(), path.segments)
    assertEquals("\\\\server", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("server", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun windowsUncAbsolutePath() {
    val path = "\\\\server\\project\\notes.txt".toPath()
    assertEquals("\\\\server".toPath(), path.root)
    assertEquals(listOf("project", "notes.txt"), path.segments)
    assertEquals("\\\\server\\project\\notes.txt", path.toString())
    assertEquals("\\\\server\\project".toPath(), path.parent)
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
    val slash = Path.DIRECTORY_SEPARATOR
    val cwd = ".".toPath()
    assertEquals("home".toPath(), cwd / "home")
    assertEquals("home${slash}jesse".toPath(), cwd / "home" / "jesse")
    assertEquals("home".toPath(), cwd / "home" / "jesse" / "..")
    assertEquals("home${slash}jake".toPath(), cwd / "home" / "jesse" / ".." / "jake")
  }

  @Test
  fun relativePathTraversalWithDots() {
    val slash = Path.DIRECTORY_SEPARATOR
    val cwd = ".".toPath()
    assertEquals("..".toPath(), cwd / "..")
    assertEquals("..$slash..".toPath(), cwd / ".." / "..")
    assertEquals("..$slash..${slash}etc".toPath(), cwd / ".." / ".." / "etc")
    assertEquals(
      "..$slash..${slash}etc${slash}passwd".toPath(),
      cwd / ".." / ".." / "etc" / "passwd"
    )
  }

  @Test
  fun pathTraversalBaseIgnoredIfChildIsAnAbsolutePath() {
    assertEquals("/home".toPath(), "".toPath() / "/home")
    assertEquals("/home".toPath(), "relative".toPath() / "/home")
    assertEquals("/home".toPath(), "/base".toPath() / "/home")
    assertEquals("/home".toPath(), "/".toPath() / "/home")
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

  @Test
  fun samePathDifferentSlashesAreNotEqual() {
    assertNotEquals("/a".toPath(), "\\b".toPath())
    assertNotEquals("a/b".toPath(), "a\\b".toPath())
  }

  @Test
  fun samePathNoSlashesAreEqual() {
    assertEquals("a".toPath().parent!!, "a".toPath().parent!!)
    assertEquals("a/b".toPath().parent!!, "a\\b".toPath().parent!!)
  }

  @Test
  fun relativeToWindowsPaths() {
    val a = "C:\\Windows\\notepad.exe".toPath()
    val b = "C:\\".toPath()
    assertRelativeTo(a, b, "..\\..".toPath(), consistentWithJavaNioPath = false)
    assertRelativeTo(b, a, "Windows\\notepad.exe".toPath(), consistentWithJavaNioPath = false)

    val c = "C:\\Windows\\".toPath()
    val d = "C:\\Windows".toPath()
    assertRelativeTo(c, d, ".".toPath())
    assertRelativeTo(d, c, ".".toPath())

    val e = "C:\\Windows\\Downloads\\".toPath()
    val f = "C:\\Windows\\Documents\\Hello.txt".toPath()
    assertRelativeTo(e, f, "..\\Documents\\Hello.txt".toPath(), consistentWithJavaNioPath = false)
    assertRelativeTo(f, e, "..\\..\\Downloads".toPath(), consistentWithJavaNioPath = false)

    val g = "C:\\Windows\\".toPath()
    val h = "D:\\Windows\\".toPath()
    assertRelativeToFails(g, h, consistentWithJavaNioPath = false)
    assertRelativeToFails(h, g, consistentWithJavaNioPath = false)
  }

  @Test
  fun relativeToWindowsUncPaths() {
    val a = "\\\\localhost\\c$\\development\\schema.proto".toPath()
    val b = "\\\\localhost\\c$\\project\\notes.txt".toPath()
    assertRelativeTo(a, b, "..\\..\\project\\notes.txt".toPath(), consistentWithJavaNioPath = false)
    assertRelativeTo(b, a, "..\\..\\development\\schema.proto".toPath(), consistentWithJavaNioPath = false)

    val c = "C:\\Windows\\".toPath()
    val d = "\\\\localhost\\c$\\project\\notes.txt".toPath()
    assertRelativeToFails(c, d, consistentWithJavaNioPath = false)
    assertRelativeToFails(d, c, consistentWithJavaNioPath = false)
  }

  @Test
  fun absoluteUnixRoot() {
    val a = "/Users/jesse/hello.txt".toPath()
    val b = "/".toPath()
    assertRelativeTo(a, b, "../../..".toPath())
    assertRelativeTo(b, a, "Users/jesse/hello.txt".toPath())

    val c = "/Users/jesse/hello.txt".toPath()
    val d = "/Admin/Secret".toPath()
    assertRelativeTo(c, d, "../../../Admin/Secret".toPath())
    assertRelativeTo(d, c, "../../Users/jesse/hello.txt".toPath())

    val e = "/Users/".toPath()
    val f = "/Users".toPath()
    assertRelativeTo(e, f, ".".toPath())
    assertRelativeTo(f, e, ".".toPath())
  }

  @Test
  fun absoluteToRelative() {
    val a = "/Users/jesse/hello.txt".toPath()
    val b = "Desktop/goodbye.txt".toPath()

    var exception = assertRelativeToFails(a, b)
    assertEquals(
      "Paths of different roots cannot be relative to each other: " +
        "Desktop/goodbye.txt and /Users/jesse/hello.txt",
      exception.message
    )

    exception = assertRelativeToFails(b, a)
    assertEquals(
      "Paths of different roots cannot be relative to each other: " +
        "/Users/jesse/hello.txt and Desktop/goodbye.txt",
      exception.message
    )
  }

  @Test
  fun absoluteToAbsolute() {
    val a = "/Users/jesse/hello.txt".toPath()
    val b = "/Users/benoit/Desktop/goodbye.txt".toPath()
    assertRelativeTo(a, b, "../../benoit/Desktop/goodbye.txt".toPath())
    assertRelativeTo(b, a, "../../../jesse/hello.txt".toPath())
  }

  @Test
  fun absoluteToSelf() {
    val a = "/Users/jesse/hello.txt".toPath()
    assertRelativeTo(a, a, ".".toPath())
  }

  @Test
  fun relativeToSelf() {
    val a = "Desktop/hello.txt".toPath()
    assertRelativeTo(a, a, ".".toPath())
  }

  @Test
  fun relativeToRelative() {
    val a = "Desktop/documents/resume.txt".toPath()
    val b = "Desktop/documents/2021/taxes.txt".toPath()
    assertRelativeTo(a, b, "../2021/taxes.txt".toPath(), consistentWithJavaNioPath = false)
    assertRelativeTo(b, a, "../../resume.txt".toPath(), consistentWithJavaNioPath = false)

    val c = "documents/resume.txt".toPath()
    val d = "downloads/2021/taxes.txt".toPath()
    assertRelativeTo(c, d, "../../downloads/2021/taxes.txt".toPath(), consistentWithJavaNioPath = false)
    assertRelativeTo(d, c, "../../../documents/resume.txt".toPath(), consistentWithJavaNioPath = false)
  }

  // TODO(bquenaudon): change Okio's behavior
  @Test
  fun relativeToRelativeWithUpNavigationPrefix() {
    // We can't navigate from 'taxes' to 'resumes' because we don't know the name of 'Documents'.
    //   /Users/jwilson/Documents/2021/Current
    //   /Users/jwilson/Documents/resumes
    //   /Users/jwilson/taxes
    val a = "../../resumes".toPath()
    val b = "../../../taxes".toPath()
    assertRelativeTo(a, b, "../../taxes".toPath())
    assertRelativeToFails(b, a, consistentWithJavaNioPath = false)
  }

  // TODO(bquenaudon): change Okio's behavior
  @Test
  fun relativeToRelativeDifferentSlashes() {
    val a = "Desktop/documents/resume.txt".toPath()
    val b = "Desktop\\documents\\2021\\taxes.txt".toPath()
    assertRelativeTo(a, b, "../2021/taxes.txt".toPath())
    assertRelativeTo(b, a, "..\\..\\resume.txt".toPath())

    val c = "documents/resume.txt".toPath()
    val d = "downloads\\2021\\taxes.txt".toPath()
    assertRelativeTo(c, d, "../../downloads/2021/taxes.txt".toPath())
    assertRelativeTo(d, c, "..\\..\\..\\documents\\resume.txt".toPath())
  }

  // TODO(bquenaudon): change Okio's behavior
  @Test
  fun windowsUncPathsDoNotDotDot() {
    assertEquals("""\\localhost\c$\Windows""", """\\localhost\c$\Windows""".toPath().toString())
    assertEquals("""\\127.0.0.1\c$\Windows""", """\\127.0.0.1\c$\Windows""".toPath().toString())
    assertEquals("""\\127.0.0.1\c$\Windows""", """\\127.0.0.1\c$\Windows\..\Windows""".toPath().toString())
    assertEquals("""\\127.0.0.1\localhost\c$\Windows""", """\\127.0.0.1\..\localhost\c$\Windows""".toPath().toString())
    // Note that this is not consistent with what Windows' own APIs do.
    // TODO(jwilson): should we normalize this?
    assertEquals("""\\127.0.0.1\d$""", """\\127.0.0.1\c$\..\d$""".toPath().toString())
  }

  // TODO(bquenaudon): change Okio's behavior
  @Test
  fun unixRelativeToWindows() {
    val a = "Desktop/documents/resume.txt".toPath()
    val b = "Desktop\\documents\\2021\\taxes.txt".toPath()

    var exception = assertRelativeToFails(a, b)
    assertEquals(
      "Paths of different platforms cannot be relative to each other: " +
        "Desktop\\documents\\2021\\taxes.txt and Desktop/documents/resume.txt",
      exception.message
    )

    exception = assertRelativeToFails(b, a)
    assertEquals(
      "Paths of different platforms cannot be relative to each other: " +
        "Desktop/documents/resume.txt and Desktop\\documents\\2021\\taxes.txt",
      exception.message
    )
  }
}
