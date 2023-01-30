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

actual abstract class ForwardingSource actual constructor(
  actual val delegate: Source,
) : Source {
  // TODO 'Source by delegate' once https://youtrack.jetbrains.com/issue/KT-23935 is fixed.

  @Throws(IOException::class)
  actual override fun read(sink: Buffer, byteCount: Long): Long = delegate.read(sink, byteCount)

  actual override fun timeout() = delegate.timeout()

  @Throws(IOException::class)
  actual override fun close() = delegate.close()

  actual override fun toString() = "${this::class.simpleName}($delegate)"
}
