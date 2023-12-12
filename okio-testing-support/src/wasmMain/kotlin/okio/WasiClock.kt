/*
 * Copyright (C) 2023 Square, Inc.
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

import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import okio.internal.preview1.clock_time_get
import okio.internal.preview1.clockid_realtime

object WasiClock : Clock {
  @OptIn(UnsafeWasmMemoryApi::class)
  override fun now(): Instant {
    withScopedMemoryAllocator { allocator ->
      val returnPointer = allocator.allocate(8) // timestamp is u64.
      val errno = clock_time_get(
        id = clockid_realtime,
        precision = 1_000_000_000L, // 1-second precision.
        returnPointer.address.toInt(),
      )
      if (errno != 0) throw IllegalStateException("failed to get now: $errno")

      val nanos = returnPointer.loadLong()
      return Instant(epochMilliseconds = nanos / 1_000_000L)
    }
  }
}
