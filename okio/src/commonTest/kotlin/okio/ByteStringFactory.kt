/*
 * Copyright (C) 2018 Square, Inc.
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

import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.internal.commonAsUtf8ToByteArray

internal interface ByteStringFactory {
  fun decodeHex(hex: String): ByteString

  fun encodeUtf8(s: String): ByteString

  companion object {
    val BYTE_STRING: ByteStringFactory = object : ByteStringFactory {
      override fun decodeHex(hex: String) = hex.decodeHex()
      override fun encodeUtf8(s: String) = s.encodeUtf8()
    }

    val SEGMENTED_BYTE_STRING: ByteStringFactory = object : ByteStringFactory {
      override fun decodeHex(hex: String) = Buffer().apply { write(hex.decodeHex()) }.snapshot()
      override fun encodeUtf8(s: String) = Buffer().apply { writeUtf8(s) }.snapshot()
    }

    val ONE_BYTE_PER_SEGMENT: ByteStringFactory = object : ByteStringFactory {
      override fun decodeHex(hex: String) = makeSegments(hex.decodeHex())
      override fun encodeUtf8(s: String) = makeSegments(s.encodeUtf8())
    }

    // For Kotlin/JVM, the native Java UTF-8 encoder is used. This forces
    // testing of the Okio encoder used for Kotlin/JS and Kotlin/Native to be
    // tested on JVM as well.
    val OKIO_ENCODER: ByteStringFactory = object : ByteStringFactory {
      override fun decodeHex(hex: String) = hex.decodeHex()
      override fun encodeUtf8(s: String) =
        ByteString.of(*s.commonAsUtf8ToByteArray())
    }
  }
}
