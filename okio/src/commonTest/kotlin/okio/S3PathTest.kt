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

import okio.ByteString.Companion.encodeUtf8
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalFilesystem
class S3PathTest {
  @Test
  fun httpsRoot() {
    val path = "https://s3.amazonaws.com/bucket/key".toPath()
    assertEquals("https://s3.amazonaws.com/bucket".toPath(), path.parent)
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("key", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
    assertEquals("/".encodeUtf8(), path.slash)
    assertEquals("https://s3.amazonaws.com/bucket/key", path.toString())
  }

  @Test
  fun s3Root() {
    val path = "s3://mybucket/argparse-1.2.1.tar.gz".toPath()
    assertEquals("s3://mybucket".toPath(), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("argparse-1.2.1.tar.gz", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
    assertEquals("/".encodeUtf8(), path.slash)
    assertEquals("s3://mybucket/argparse-1.2.1.tar.gz", path.toString())
  }

  @Test
  fun s3Relative() {
    val path = "mybucket/argparse-1.2.1.tar.gz".toPath()
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("argparse-1.2.1.tar.gz", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
    assertEquals("/".encodeUtf8(), path.slash)
    assertEquals("mybucket/argparse-1.2.1.tar.gz", path.toString())
  }
}
