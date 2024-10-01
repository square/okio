/*
 * Copyright (C) 2024 Square, Inc.
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
import kotlin.test.assertFailsWith
import okio.ByteString.Companion.encodeUtf8

class TypedOptionsTest {
  @Test
  fun happyPath() {
    val colors = listOf("Red", "Green", "Blue")
    val colorOptions = TypedOptions.of(colors) { it.lowercase().encodeUtf8() }
    val buffer = Buffer().writeUtf8("bluegreenyellow")
    assertEquals("Blue", buffer.select(colorOptions))
    assertEquals("greenyellow", buffer.snapshot().utf8())
    assertEquals("Green", buffer.select(colorOptions))
    assertEquals("yellow", buffer.snapshot().utf8())
    assertEquals(null, buffer.select(colorOptions))
    assertEquals("yellow", buffer.snapshot().utf8())
  }

  @Test
  fun typedOptionsConstructor() {
    val colors = listOf("Red", "Green", "Blue")
    val colorOptions = TypedOptions(
      colors,
      Options.of("red".encodeUtf8(), "green".encodeUtf8(), "blue".encodeUtf8()),
    )
    val buffer = Buffer().writeUtf8("bluegreenyellow")
    assertEquals("Blue", buffer.select(colorOptions))
    assertEquals("greenyellow", buffer.snapshot().utf8())
    assertEquals("Green", buffer.select(colorOptions))
    assertEquals("yellow", buffer.snapshot().utf8())
    assertEquals(null, buffer.select(colorOptions))
    assertEquals("yellow", buffer.snapshot().utf8())
  }

  @Test
  fun typedOptionsConstructorEnforcesSizeMatch() {
    val colors = listOf("Red", "Green", "Blue")
    assertFailsWith<IllegalArgumentException> {
      TypedOptions(
        colors,
        Options.of("red".encodeUtf8(), "green".encodeUtf8()),
      )
    }
  }

  @Test
  fun listFunctionsWork() {
    val colors = listOf("Red", "Green", "Blue")
    val colorOptions = TypedOptions.of(colors) { it.lowercase().encodeUtf8() }
    assertEquals(3, colorOptions.size)
    assertEquals("Red", colorOptions[0])
    assertEquals("Green", colorOptions[1])
    assertEquals("Blue", colorOptions[2])
    assertFailsWith<IndexOutOfBoundsException> {
      colorOptions[3]
    }
  }

  /**
   * Confirm we can mutate the collection used to create our [TypedOptions] without corrupting its
   * behavior.
   */
  @Test
  fun safeToMutateSourceCollectionAfterConstruction() {
    val colors = mutableListOf("Red", "Green")
    val colorOptions = TypedOptions.of(colors) { it.lowercase().encodeUtf8() }
    colors[0] = "Black"

    val buffer = Buffer().writeUtf8("red")
    assertEquals("Red", buffer.select(colorOptions))
  }
}
