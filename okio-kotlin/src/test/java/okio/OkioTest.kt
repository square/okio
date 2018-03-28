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

package okio

import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import sun.plugin2.os.windows.Windows.CREATE_NEW
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.file.StandardOpenOption
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE

class OkioTest {
  @get:Rule val temp = TemporaryFolder()

  @Test fun sourceBuffer() {
    val source = Buffer().writeUtf8("a")
    val buffered = (source as Source).buffer()
    assertThat(buffered.readUtf8()).isEqualTo("a")
    assertThat(source.size()).isEqualTo(0)
  }

  @Test fun sinkBuffer() {
    val sink = Buffer()
    val buffered = (sink as Sink).buffer()
    buffered.writeUtf8("a")
    assertThat(sink.size()).isEqualTo(0)
    buffered.flush()
    assertThat(sink.size()).isEqualTo(1)
  }

  @Test fun outputStreamSink() {
    val baos = ByteArrayOutputStream()
    val sink = baos.sink()
    sink.write(Buffer().writeUtf8("a"), 1)
    assertThat(baos.toByteArray()).isEqualTo(byteArrayOf(0x61))
  }

  @Test fun inputStreamSource() {
    val bais = ByteArrayInputStream(byteArrayOf(0x61))
    val source = bais.source()
    val buffer = Buffer()
    source.read(buffer, 1)
    assertThat(buffer.readUtf8()).isEqualTo("a")
  }

  @Test fun fileSink() {
    val file = temp.newFile()
    val sink = file.sink()
    sink.write(Buffer().writeUtf8("a"), 1)
    assertThat(file.readText()).isEqualTo("a")
  }

  @Test fun fileAppendingSink() {
    val file = temp.newFile()
    file.writeText("a")
    val sink = file.sink(append = true)
    sink.write(Buffer().writeUtf8("b"), 1)
    sink.close()
    assertThat(file.readText()).isEqualTo("ab")
  }

  @Test fun fileSource() {
    val file = temp.newFile()
    file.writeText("a")
    val source = file.source()
    val buffer = Buffer()
    source.read(buffer, 1)
    assertThat(buffer.readUtf8()).isEqualTo("a")
  }

  @Test fun pathSink() {
    val file = temp.newFile()
    val sink = file.toPath().sink()
    sink.write(Buffer().writeUtf8("a"), 1)
    assertThat(file.readText()).isEqualTo("a")
  }

  @Test fun pathSinkWithOptions() {
    val file = temp.newFile()
    file.writeText("a")
    val sink = file.toPath().sink(APPEND)
    sink.write(Buffer().writeUtf8("b"), 1)
    assertThat(file.readText()).isEqualTo("ab")
  }

  @Test fun pathSource() {
    val file = temp.newFile()
    file.writeText("a")
    val source = file.toPath().source()
    val buffer = Buffer()
    source.read(buffer, 1)
    assertThat(buffer.readUtf8()).isEqualTo("a")
  }

  @Ignore("Not sure how to test this")
  @Test fun pathSourceWithOptions() {
    val folder = temp.newFolder()
    val file = File(folder, "new.txt")
    file.toPath().source(StandardOpenOption.CREATE_NEW)
    // This still throws NoSuchFileException...
  }

  @Test fun blackhole() {
    blackholeSink().write(Buffer().writeUtf8("a"), 1)
  }

  @Test fun socketSink() {
    val baos = ByteArrayOutputStream()
    val socket = object : Socket() {
      override fun getOutputStream() = baos
    }
    val sink = socket.sink()
    sink.write(Buffer().writeUtf8("a"), 1)
    assertThat(baos.toByteArray()).isEqualTo(byteArrayOf(0x61))
  }

  @Test fun socketSource() {
    val bais = ByteArrayInputStream(byteArrayOf(0x61))
    val socket = object : Socket() {
      override fun getInputStream() = bais
    }
    val source = socket.source()
    val buffer = Buffer()
    source.read(buffer, 1)
    assertThat(buffer.readUtf8()).isEqualTo("a")
  }
}
