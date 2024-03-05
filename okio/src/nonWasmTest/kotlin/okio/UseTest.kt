package okio

import kotlin.test.Test
import kotlin.test.assertNull
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class UseTest {
  val fakeFileSystem = FakeFileSystem(clock = FakeClock()).also { it.emulateUnix() }

  val base = "/cache".toPath().also {
    fakeFileSystem.createDirectories(it)
  }

  @Test
  fun closesWithUseBlock() {
    fun testMethodWithUse() {
      val sink = fakeFileSystem.sink(base / "all-files-includes-file")

      sink.use {
        return@testMethodWithUse
      }
    }

    testMethodWithUse()

    fakeFileSystem.checkNoOpenFiles()
  }

  @Test
  fun acceptsNullReturn() {
    val result = object : Closeable {
      override fun close() {}
    }.use { null }

    assertNull(result)
  }
}
