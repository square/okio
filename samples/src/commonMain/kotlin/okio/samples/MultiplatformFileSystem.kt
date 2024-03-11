package okio.samples

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

class MultiplatformFileSystem {
  fun run() {
    val currentWorkingDirectory = FileSystem.SYSTEM.canonicalize("".toPath())
    val children = FileSystem.SYSTEM.listOrNull(currentWorkingDirectory)
    println("The current working directory is: $currentWorkingDirectory, containing: ${children?.joinToString()}")
  }
}

fun main() {
  MultiplatformFileSystem().run()
}
