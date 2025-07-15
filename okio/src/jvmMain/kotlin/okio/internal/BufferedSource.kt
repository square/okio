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

import java.util.Spliterator
import java.util.Spliterators
import java.util.stream.Stream
import java.util.stream.StreamSupport
import okio.BufferedSource

internal inline fun BufferedSource.commonUtf8Lines(): Stream<String> {
  return StreamSupport.stream(
    Spliterators.spliteratorUnknownSize(commonUtf8LineIterator(), Spliterator.ORDERED or Spliterator.NONNULL),
    false
  )
}
