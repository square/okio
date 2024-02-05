/*
 * Copyright (C) 2024 Square, Inc.
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

import kotlin.jvm.JvmStatic

/**
 * A list of values that may be read with [BufferedSource.select].
 *
 * Also consider [Options] to select an integer index.
 */
class TypedOptions<T : Any>(
  list: List<T>,
  internal val options: Options,
) : AbstractList<T>(), RandomAccess {
  internal val list = list.toList() // Defensive copy.

  init {
    require(this.list.size == options.size)
  }

  override val size: Int
    get() = list.size

  override fun get(index: Int) = list[index]

  companion object {
    @JvmStatic
    inline fun <T : Any> of(
      values: Iterable<T>,
      encode: (T) -> ByteString,
    ): TypedOptions<T> {
      val list = values.toList()
      val options = Options.of(*Array(list.size) { encode(list[it]) })
      return TypedOptions(list, options)
    }
  }
}
