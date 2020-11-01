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
package okio.files

import okio.Filesystem
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import kotlin.test.Test

class JvmSystemFilesystemTest {
  @Test
  fun `cwd consistent with java io File`() {
    assertThat(Filesystem.SYSTEM.cwd.toString()).isEqualTo(File("").absoluteFile.toString())
  }
}
