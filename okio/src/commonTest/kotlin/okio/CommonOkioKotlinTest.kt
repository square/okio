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

package okio

import kotlin.test.Test
import kotlin.test.assertEquals

class CommonOkioKotlinTest {
  @Test fun sourceBuffer() {
    val source = Buffer().writeUtf8("a")
    val buffered = (source as Source).buffer()
    assertEquals(buffered.readUtf8(), "a")
    assertEquals(source.size, 0L)
  }

  @Test fun sinkBuffer() {
    val sink = Buffer()
    val buffered = (sink as Sink).buffer()
    buffered.writeUtf8("a")
    assertEquals(sink.size, 0L)
    buffered.flush()
    assertEquals(sink.size, 1L)
  }

  @Test fun blackhole() {
    blackholeSink().write(Buffer().writeUtf8("a"), 1L)
  }
}
