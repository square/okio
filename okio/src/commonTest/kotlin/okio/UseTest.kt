package okio

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test

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
}
