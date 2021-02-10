/*
 * Copyright (C) 2021 Square, Inc.
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

/**
 * A position in a stream. This is an optional feature of [Source] to support random access. Read
 * and write operations implicitly advance the position by the number of bytes processed.
 */
interface Cursor {
  /**
   * Returns the position in the stream. This is a value between 0 and the file size, inclusive. If
   * this equals the file size then the stream is exhausted for reading, and reads will return -1.
   */
  @Throws(IOException::class)
  fun position(): Long

  /**
   * Returns the total number of bytes in the stream. This will change if the stream size changes.
   */
  @Throws(IOException::class)
  fun size(): Long

  /**
   * Adjust the position within the stream. This may be used to advance forward or backward in the
   * stream, skipping data or reading data repeatedly.
   */
  @Throws(IOException::class)
  fun seek(position: Long)
}
