/*
 * Copyright (C) 2018 Square, Inc.
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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within

/** Stopwatch for asserting elapsed time during unit tests. */
internal class Stopwatch {
  private val start = System.nanoTime() / 1e9
  private var offset = 0.0

  /**
   * Fails the test unless the time from the last assertion until now is `elapsed`, accepting
   * differences in -200..+200 milliseconds.
   */
  fun assertElapsed(elapsed: Double) {
    offset += elapsed
    assertThat(System.nanoTime() / 1e9 - start).isCloseTo(offset, within(0.2))
  }
}
