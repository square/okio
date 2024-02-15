/*
 * Copyright (C) 2024 Square, Inc.
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

@file:JvmName("-DeflaterSinkExtensions")
package okio

import kotlin.jvm.JvmName

expect class DeflaterSink internal constructor(
  sink: BufferedSink,
  deflater: Deflater,
) : Sink {
  constructor(sink: Sink, deflater: Deflater)

  internal fun finishDeflate()
}

/**
 * Returns an [DeflaterSink] that DEFLATE-compresses data to this [Sink] while writing.
 *
 * @see DeflaterSink
 */
inline fun Sink.deflate(deflater: Deflater = Deflater()): DeflaterSink =
  DeflaterSink(this, deflater)
