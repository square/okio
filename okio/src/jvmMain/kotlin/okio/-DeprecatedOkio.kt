// ktlint-disable filename
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

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.file.OpenOption
import java.nio.file.Path as NioPath

@Deprecated(message = "changed in Okio 2.x")
object `-DeprecatedOkio` {
  @Deprecated(
    message = "moved to extension function",
    replaceWith = ReplaceWith(
      expression = "file.appendingSink()",
      imports = ["okio.appendingSink"],
    ),
    level = DeprecationLevel.ERROR,
  )
  fun appendingSink(file: File) = file.appendingSink()

  @Deprecated(
    message = "moved to extension function",
    replaceWith = ReplaceWith(
      expression = "sink.buffer()",
      imports = ["okio.buffer"],
    ),
    level = DeprecationLevel.ERROR,
  )
  fun buffer(sink: Sink) = sink.buffer()

  @Deprecated(
    message = "moved to extension function",
    replaceWith = ReplaceWith(
      expression = "source.buffer()",
      imports = ["okio.buffer"],
    ),
    level = DeprecationLevel.ERROR,
  )
  fun buffer(source: Source) = source.buffer()

  @Deprecated(
    message = "moved to extension function",
    replaceWith = ReplaceWith(
      expression = "file.sink()",
      imports = ["okio.sink"],
    ),
    level = DeprecationLevel.ERROR,
  )
  fun sink(file: File) = file.sink()

  @Deprecated(
    message = "moved to extension function",
    replaceWith = ReplaceWith(
      expression = "outputStream.sink()",
      imports = ["okio.sink"],
    ),
    level = DeprecationLevel.ERROR,
  )
  fun sink(outputStream: OutputStream) = outputStream.sink()

  @Deprecated(
    message = "moved to extension function",
    replaceWith = ReplaceWith(
      expression = "path.sink(*options)",
      imports = ["okio.sink"],
    ),
    level = DeprecationLevel.ERROR,
  )
  fun sink(path: NioPath, vararg options: OpenOption) = path.sink(*options)

  @Deprecated(
    message = "moved to extension function",
    replaceWith = ReplaceWith(
      expression = "socket.sink()",
      imports = ["okio.sink"],
    ),
    level = DeprecationLevel.ERROR,
  )
  fun sink(socket: Socket) = socket.sink()

  @Deprecated(
    message = "moved to extension function",
    replaceWith = ReplaceWith(
      expression = "file.source()",
      imports = ["okio.source"],
    ),
    level = DeprecationLevel.ERROR,
  )
  fun source(file: File) = file.source()

  @Deprecated(
    message = "moved to extension function",
    replaceWith = ReplaceWith(
      expression = "inputStream.source()",
      imports = ["okio.source"],
    ),
    level = DeprecationLevel.ERROR,
  )
  fun source(inputStream: InputStream) = inputStream.source()

  @Deprecated(
    message = "moved to extension function",
    replaceWith = ReplaceWith(
      expression = "path.source(*options)",
      imports = ["okio.source"],
    ),
    level = DeprecationLevel.ERROR,
  )
  fun source(path: NioPath, vararg options: OpenOption) = path.source(*options)

  @Deprecated(
    message = "moved to extension function",
    replaceWith = ReplaceWith(
      expression = "socket.source()",
      imports = ["okio.source"],
    ),
    level = DeprecationLevel.ERROR,
  )
  fun source(socket: Socket) = socket.source()

  @Deprecated(
    message = "moved to extension function",
    replaceWith = ReplaceWith(
      expression = "blackholeSink()",
      imports = ["okio.blackholeSink"],
    ),
    level = DeprecationLevel.ERROR,
  )
  fun blackhole() = blackholeSink()
}
