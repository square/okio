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
package okio

private val emptyByteArray = byteArrayOf()

/**
 * Transform a stream of source bytes into a stream of target bytes, one segment at a time. The
 * relationship between input byte count and output byte count is arbitrary: a sequence of input
 * bytes may produce zero output bytes, or many segments of output bytes.
 *
 * To use:
 *
 *  1. Create an instance.
 *
 *  2. Populate [source] with input data. Set [sourcePos] and [sourceLimit] to a readable slice of
 *     this array.
 *
 *  3. Populate [target] with a destination for output data. Set [targetPos] and [targetLimit] to a
 *     writable slice of this array.
 *
 *  4. Call [process] to read input data from [source] and write output to [target]. This function
 *     advances [sourcePos] if input data was read and [targetPos] if compressed output was written.
 *     If the input array is exhausted (`sourcePos == sourceLimit`) or the output array is full
 *     (`targetPos == targetLimit`), make an adjustment and call [process] again.
 *
 *  5. Repeat steps 2 through 4 until the input data is completely exhausted.
 *
 *  6. Close the processor.
 *
 * See also, the [zlib manual](https://www.zlib.net/manual.html).
 */
internal abstract class DataProcessor : Closeable {
  var source: ByteArray = emptyByteArray
  var sourcePos: Int = 0
  var sourceLimit: Int = 0

  var target: ByteArray = emptyByteArray
  var targetPos: Int = 0
  var targetLimit: Int = 0

  var closed: Boolean = false
    protected set

  /**
   * Returns true if no further calls to [process] are required to complete the operation.
   * Otherwise, make space available in [target] and call [process] again.
   */
  @Throws(ProtocolException::class)
  abstract fun process(): Boolean
}
