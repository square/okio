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

// TODO remove after https://youtrack.jetbrains.com/issue/KT-24478
@Target(FUNCTION)
expect annotation class JvmOverloads()

// TODO remove after https://youtrack.jetbrains.com/issue/KT-24478
@Target(FIELD)
expect annotation class JvmField()

// TODO remove after https://youtrack.jetbrains.com/issue/KT-24478
@Target(FUNCTION)
expect annotation class JvmStatic()

// TODO remove after https://youtrack.jetbrains.com/issue/KT-24478
@Target(FILE, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
expect annotation class JvmName(val name: String)

internal expect fun arraycopy(
  src: ByteArray,
  srcPos: Int,
  dest: ByteArray,
  destPos: Int,
  length: Int
)

internal expect fun ByteArray.toUtf8String(): String

internal expect fun String.asUtf8ToByteArray(): ByteArray

// TODO make internal https://youtrack.jetbrains.com/issue/KT-19664
expect class ArrayIndexOutOfBoundsException(message: String?) : IndexOutOfBoundsException

internal expect inline fun <R> synchronized(lock: Any, block: () -> R): R
