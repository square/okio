/*
 * Copyright (C) 2018 Square, Inc.
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
package okio.samples

import java.io.IOException
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.FileSystem
import okio.HashingSink.Companion.sha256
import okio.HashingSource.Companion.sha256
import okio.Path
import okio.Path.Companion.toPath
import okio.blackholeSink
import okio.buffer

class KotlinHashing {
  fun run() {
    val path = "../README.md".toPath()

    println("ByteString")
    val byteString = readByteString(path)
    println("       md5: " + byteString.md5().hex())
    println("      sha1: " + byteString.sha1().hex())
    println("    sha256: " + byteString.sha256().hex())
    println("    sha512: " + byteString.sha512().hex())
    println()

    println("Buffer")
    val buffer = readBuffer(path)
    println("       md5: " + buffer.md5().hex())
    println("      sha1: " + buffer.sha1().hex())
    println("    sha256: " + buffer.sha256().hex())
    println("    sha512: " + buffer.sha512().hex())
    println()

    println("HashingSource")
    sha256(FileSystem.SYSTEM.source(path)).use { hashingSource ->
      hashingSource.buffer().use { source ->
        source.readAll(blackholeSink())
        println("    sha256: " + hashingSource.hash.hex())
      }
    }
    println()

    println("HashingSink")
    sha256(blackholeSink()).use { hashingSink ->
      hashingSink.buffer().use { sink ->
        FileSystem.SYSTEM.source(path).use { source ->
          sink.writeAll(source)
          sink.close() // Emit anything buffered.
          println("    sha256: " + hashingSink.hash.hex())
        }
      }
    }
    println()

    println("HMAC")
    val secret = "7065616e7574627574746572".decodeHex()
    println("hmacSha256: " + byteString.hmacSha256(secret).hex())
    println()
  }

  @Throws(IOException::class)
  fun readByteString(path: Path): ByteString {
    return FileSystem.SYSTEM.read(path) { readByteString() }
  }

  @Throws(IOException::class)
  fun readBuffer(path: Path): Buffer {
    FileSystem.SYSTEM.read(path) {
      val result = Buffer()
      readAll(result)
      return result
    }
  }
}

fun main() {
  KotlinHashing().run()
}
