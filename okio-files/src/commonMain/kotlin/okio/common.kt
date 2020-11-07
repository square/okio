/*
 * Copyright (C) 2020 Square, Inc.
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

internal fun Filesystem.commonCopy(
  source: Path,
  target: Path
) {
  source(source).use { bytesIn ->
    sink(target).buffer().use { bytesOut ->
      bytesOut.writeAll(bytesIn)
    }
  }
}

internal inline fun <S : Source, T> S.use(block: (S) -> T): T {
  var result: T? = null
  var thrown: Throwable? = null

  try {
    result = block(this)
  } catch (t: Throwable) {
    thrown = t
  }

  try {
    close()
  } catch (t: Throwable) {
    if (thrown == null) thrown = t
    else thrown.addSuppressed(t)
  }

  if (thrown != null) throw thrown
  return result!!
}

internal inline fun <S : Sink, T> S.use(block: (S) -> T): T {
  var result: T? = null
  var thrown: Throwable? = null

  try {
    result = block(this)
  } catch (t: Throwable) {
    thrown = t
  }

  try {
    close()
  } catch (t: Throwable) {
    if (thrown == null) thrown = t
    else thrown.addSuppressed(t)
  }

  if (thrown != null) throw thrown
  return result!!
}
