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

import okio.internal.commonAsUtf8ToByteArray
import okio.internal.commonToUtf8String

internal actual fun ByteArray.toUtf8String(): String = commonToUtf8String()

// We are NOT using the Kotlin/Native function `toUtf8()` because it encodes
// invalide UTF-16 characters as '\ufffd' instead of '?' like Okio does. In an
// effort to keep the library consistent with itself, we use the Okio encoder
// implementation instead. This will hopefully help avoid weird gotcha's as this
// library starts to be used more across platforms.
internal actual fun String.asUtf8ToByteArray(): ByteArray = commonAsUtf8ToByteArray()

actual typealias ArrayIndexOutOfBoundsException = kotlin.ArrayIndexOutOfBoundsException

internal actual inline fun <R> synchronized(lock: Any, block: () -> R): R = block()

actual open class IOException actual constructor(message: String?) : Exception(message)

actual open class EOFException actual constructor(message: String?) : IOException(message)
