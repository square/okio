/*
 * Copyright (C) 2019 Square, Inc.
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
@file:JvmName("-BufferedSourceJVM") // A leading '-' hides this class from Java.

package okio.internal

import java.util.PrimitiveIterator
import java.util.Spliterator
import java.util.Spliterators
import java.util.stream.IntStream
import java.util.stream.StreamSupport
import okio.BufferedSource

internal fun BufferedSource.commonUtf8CodePoints(): IntStream {
  val iterator = object : PrimitiveIterator.OfInt {
    override fun nextInt(): Int {
      if (exhausted()) throw NoSuchElementException()
      return readUtf8CodePoint()
    }

    override fun remove() = throw UnsupportedOperationException()

    override fun hasNext(): Boolean = !exhausted()
  }

  return StreamSupport.intStream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
}
