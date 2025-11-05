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
@file:JvmName("SystemFileSystem")
@file:MustUseReturnValue

package okio

/*
 * JVM and native platforms do offer a [SYSTEM] [FileSystem], however we cannot refine an 'expect' companion object.
 * Therefore an extension property is provided, which on respective platforms (here JVM) will be shadowed by the
 * original implementation.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
actual inline val FileSystem.Companion.SYSTEM: FileSystem
  @JvmSynthetic
  get() = SYSTEM
