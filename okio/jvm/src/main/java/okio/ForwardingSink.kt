/*
 * Copyright (C) 2014 Square, Inc.
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

import java.io.IOException

/** A [Sink] which forwards calls to another. Useful for subclassing. */
abstract class ForwardingSink(
  /** [Sink] to which this instance is delegating. */
  @get:JvmName("delegate")
  val delegate: Sink
) : Sink {
  // TODO 'Sink by delegate' once https://youtrack.jetbrains.com/issue/KT-23935 is fixed.

  @Throws(IOException::class)
  override fun write(source: Buffer, byteCount: Long) = delegate.write(source, byteCount)

  @Throws(IOException::class)
  override fun flush() = delegate.flush()

  override fun timeout() = delegate.timeout()

  @Throws(IOException::class)
  override fun close() = delegate.close()

  override fun toString() = "${javaClass.simpleName}($delegate)"

  @JvmName("-deprecated_delegate")
  @Deprecated(
      message = "moved to val",
      replaceWith = ReplaceWith(expression = "delegate"),
      level = DeprecationLevel.ERROR)
  fun delegate() = delegate
}
