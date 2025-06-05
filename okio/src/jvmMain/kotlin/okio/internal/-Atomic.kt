// ktlint-disable filename
/*
 * Copyright (C) 2025 Square, Inc.
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
package okio.internal

import java.util.concurrent.atomic.AtomicInteger

/**
 * Returns the new value of the bit field if a change was made, or 0 if no change was made.
 */
internal fun AtomicInteger.setBitOrZero(bit: Int): Int {
  while (true) {
    val current = get()
    if (current and bit != 0) return 0 // Bit is already set.
    val updated = current or bit
    if (compareAndSet(current, updated)) return updated
  }
}
