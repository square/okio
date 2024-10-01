/*
 * Copyright (C) 2024 Square, Inc.
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
package okio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath

/**
 * Test using sample data from Go's test suite.
 *
 * https://github.com/golang/go/blob/6f5d77454e31be8af11a7e2bcda36d200fda07c5/src/archive/zip/reader_test.go
 */
class ZipFileSystemGoTest {
  private val fileSystem = FileSystem.SYSTEM
  private var base = okioRoot / "okio-testing-support" /
    "src/commonMain/resources/go/src/archive/zip/testdata"

  @Test
  fun timeWinzip() {
    val zipFileSystem = fileSystem.openZip(base / "time-winzip.zip")
    val fileMetadata = zipFileSystem.metadata("test.txt".toPath())
    assertEquals(
      Instant.parse("2017-11-01T04:11:57.244Z"),
      Instant.fromEpochMilliseconds(fileMetadata.createdAtMillis!!),
    )
    assertEquals(
      Instant.parse("2017-11-01T04:11:57.244Z"),
      Instant.fromEpochMilliseconds(fileMetadata.lastModifiedAtMillis!!),
    )
    assertEquals(
      Instant.parse("2017-11-01T04:13:19.623Z"),
      Instant.fromEpochMilliseconds(fileMetadata.lastAccessedAtMillis!!),
    )
  }
}
