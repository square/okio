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
import org.junit.Test

class GzipSourceTest {
  @Test fun gzip() {
    val buffer = Buffer().write(ByteString.decodeHex("1f8b0800000000000000f3c8540400dac59e7903000000"))
    val gzip = (buffer as Source).gzip()
    assertThat(Okio.buffer(gzip).readUtf8()).isEqualTo("Hi!")
  }
}
