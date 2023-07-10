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

import kotlin.test.fail
import okio.internal.FixedLengthSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

internal class FixedLengthSourceTest {
  @Test
  fun happyPathWithTruncate() {
    val delegate = Buffer().writeUtf8("abcdefghijklmnop")
    val fixedLengthSource = FixedLengthSource(delegate, 16, truncate = true)
    val buffer = Buffer()
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(10L)
    assertThat(buffer.readUtf8()).isEqualTo("abcdefghij")
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(6L)
    assertThat(buffer.readUtf8()).isEqualTo("klmnop")
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(-1L)
    assertThat(buffer.readUtf8()).isEqualTo("")
  }

  @Test
  fun happyPathNoTruncate() {
    val delegate = Buffer().writeUtf8("abcdefghijklmnop")
    val fixedLengthSource = FixedLengthSource(delegate, 16, truncate = false)
    val buffer = Buffer()
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(10L)
    assertThat(buffer.readUtf8()).isEqualTo("abcdefghij")
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(6L)
    assertThat(buffer.readUtf8()).isEqualTo("klmnop")
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(-1L)
    assertThat(buffer.readUtf8()).isEqualTo("")
  }

  @Test
  fun delegateTooLongWithTruncate() {
    val delegate = Buffer().writeUtf8("abcdefghijklmnopqr")
    val fixedLengthSource = FixedLengthSource(delegate, 16, truncate = true)
    val buffer = Buffer()
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(10L)
    assertThat(buffer.readUtf8()).isEqualTo("abcdefghij")
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(6L)
    assertThat(buffer.readUtf8()).isEqualTo("klmnop")
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(-1L)
    assertThat(buffer.readUtf8()).isEqualTo("")
  }

  @Test
  fun delegateTooLongWithTruncateFencepost() {
    val delegate = Buffer().writeUtf8("abcdefghijklmnop")
    val fixedLengthSource = FixedLengthSource(delegate, 10, truncate = true)
    val buffer = Buffer()
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(10L)
    assertThat(buffer.readUtf8()).isEqualTo("abcdefghij")
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(-1L)
    assertThat(buffer.readUtf8()).isEmpty()
  }

  @Test
  fun delegateTooLongNoTruncate() {
    val delegate = Buffer().writeUtf8("abcdefghijklmnopqr")
    val fixedLengthSource = FixedLengthSource(delegate, 16, truncate = false)
    val buffer = Buffer()
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(10L)
    assertThat(buffer.readUtf8()).isEqualTo("abcdefghij")
    try {
      fixedLengthSource.read(buffer, 10L)
      fail()
    } catch (e: IOException) {
      assertThat(e).hasMessage("expected 16 bytes but got 18")
      assertThat(buffer.readUtf8()).isEqualTo("klmnop") // Doesn't produce too many bytes!
    }
    try {
      fixedLengthSource.read(buffer, 10L)
      fail()
    } catch (e: IOException) {
      assertThat(e).hasMessage("expected 16 bytes but got 18")
      assertThat(buffer.readUtf8()).isEmpty() // Doesn't produce any bytes!
    }
  }

  @Test
  fun delegateTooLongNoTruncateFencepost() {
    val delegate = Buffer().writeUtf8("abcdefghijklmnop")
    val fixedLengthSource = FixedLengthSource(delegate, 10, truncate = false)
    val buffer = Buffer()
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(10L)
    assertThat(buffer.readUtf8()).isEqualTo("abcdefghij")
    try {
      fixedLengthSource.read(buffer, 10L)
      fail()
    } catch (e: IOException) {
      assertThat(e).hasMessage("expected 10 bytes but got 16")
      assertThat(buffer.readUtf8()).isEmpty() // Doesn't produce too many bytes!
    }
    try {
      fixedLengthSource.read(buffer, 10L)
      fail()
    } catch (e: IOException) {
      assertThat(e).hasMessage("expected 10 bytes but got 16")
      assertThat(buffer.readUtf8()).isEmpty() // Doesn't produce any bytes!
    }
  }

  @Test
  fun delegateTooShortWithTruncate() {
    val delegate = Buffer().writeUtf8("abcdefghijklmn")
    val fixedLengthSource = FixedLengthSource(delegate, 16, truncate = true)
    val buffer = Buffer()
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(10L)
    assertThat(buffer.readUtf8()).isEqualTo("abcdefghij")
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(4L)
    assertThat(buffer.readUtf8()).isEqualTo("klmn")
    try {
      fixedLengthSource.read(buffer, 10L)
      fail()
    } catch (e: IOException) {
      assertThat(e).hasMessage("expected 16 bytes but got 14")
    }
    try {
      fixedLengthSource.read(buffer, 10L)
      fail()
    } catch (e: IOException) {
      assertThat(e).hasMessage("expected 16 bytes but got 14")
    }
  }

  @Test
  fun delegateTooShortNoTruncate() {
    val delegate = Buffer().writeUtf8("abcdefghijklmn")
    val fixedLengthSource = FixedLengthSource(delegate, 16, truncate = false)
    val buffer = Buffer()
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(10L)
    assertThat(buffer.readUtf8()).isEqualTo("abcdefghij")
    assertThat(fixedLengthSource.read(buffer, 10L)).isEqualTo(4L)
    assertThat(buffer.readUtf8()).isEqualTo("klmn")
    try {
      fixedLengthSource.read(buffer, 10L)
      fail()
    } catch (e: IOException) {
      assertThat(e).hasMessage("expected 16 bytes but got 14")
    }
    try {
      fixedLengthSource.read(buffer, 10L)
      fail()
    } catch (e: IOException) {
      assertThat(e).hasMessage("expected 16 bytes but got 14")
    }
  }
}
