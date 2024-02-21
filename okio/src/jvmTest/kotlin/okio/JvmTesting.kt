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

import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath

actual fun assertRelativeTo(
  a: Path,
  b: Path,
  bRelativeToA: Path,
  sameAsNio: Boolean,
) {
  val actual = b.relativeTo(a)
  assertEquals(bRelativeToA, actual)
  assertEquals(b.normalized().withUnixSlashes(), (a / actual).normalized().withUnixSlashes())
  // Also confirm our behavior is consistent with java.nio.
  if (sameAsNio) {
    // On Windows, java.nio will modify slashes to backslashes for relative paths, so we force it.
    val nioPath = a.toNioPath().relativize(b.toNioPath())
      .toOkioPath(normalize = true).withUnixSlashes().toPath()
    assertEquals(bRelativeToA, nioPath)
  }
}

actual fun assertRelativeToFails(
  a: Path,
  b: Path,
  sameAsNio: Boolean,
): IllegalArgumentException {
  // Check java.nio first.
  if (sameAsNio) {
    assertFailsWith<IllegalArgumentException> {
      a.toNioPath().relativize(b.toNioPath())
    }
  }
  // Return okio.
  return assertFailsWith { b.relativeTo(a) }
}

actual fun <T> withUtc(block: () -> T): T {
  val original = TimeZone.getDefault()
  TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
  try {
    return block()
  } finally {
    TimeZone.setDefault(original)
  }
}
