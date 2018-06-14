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

import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.FILE
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER

@Target(FUNCTION)
actual annotation class JvmOverloads

@Target(FIELD)
actual annotation class JvmField

@Target(FUNCTION)
actual annotation class JvmStatic

@Target(FILE, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
actual annotation class JvmName(actual val name: String)

internal actual fun arraycopy(
  src: ByteArray,
  srcPos: Int,
  dest: ByteArray,
  destPos: Int,
  length: Int
) {
  for (i in 0 until length) {
    dest[destPos + i] = src[srcPos + i]
  }
}

internal actual fun hashCode(a: ByteArray): Int {
  var result = 1
  for (element in a) {
    result = 31 * result + element
  }
  return result
}

internal actual fun ByteArray.toUtf8String(): String = "" // TODO

internal actual fun CharArray.createString(): String = buildString {
  for (c in this) append(c)
}

internal actual fun String.asUtf8ToByteArray(): ByteArray = byteArrayOf() // TODO

// TODO remove if https://youtrack.jetbrains.com/issue/KT-20641 provides a better solution
actual open class IndexOutOfBoundsException actual constructor(
  message: String
) : RuntimeException()

actual open class ArrayIndexOutOfBoundsException actual constructor(
  message: String
) : IndexOutOfBoundsException(message)
