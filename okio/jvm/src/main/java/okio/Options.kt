/*
 * Copyright (C) 2016 Square, Inc.
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

/** An indexed set of values that may be read with [BufferedSource.select].  */
class Options private constructor(
  internal val byteStrings: Array<out ByteString>
) : AbstractList<ByteString>(), RandomAccess {

  override val size: Int
    get() = byteStrings.size

  override fun get(index: Int) = byteStrings[index]

  companion object {
    @JvmStatic
    fun of(vararg byteStrings: ByteString): Options {
      return Options(byteStrings.clone()) // Defensive copy.
    }
  }
}
