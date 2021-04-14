/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package okio

internal expect fun ByteArray.toUtf8String(): String

internal expect fun String.asUtf8ToByteArray(): ByteArray

// TODO make internal https://youtrack.jetbrains.com/issue/KT-37316
expect class ArrayIndexOutOfBoundsException(message: String?) : IndexOutOfBoundsException

internal expect inline fun <R> synchronized(lock: Any, block: () -> R): R

expect open class IOException(message: String?, cause: Throwable?) : Exception {
  constructor(message: String? = null)
}

expect open class EOFException(message: String? = null) : IOException

expect class FileNotFoundException(message: String? = null) : IOException

expect interface Closeable {
  /**
   * Closes this object and releases the resources it holds. It is an error to use an object after
   * it has been closed. It is safe to close an object more than once.
   */
  @Throws(IOException::class)
  fun close()
}
