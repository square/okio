/*
 * Copyright (C) 2020 Square, Inc. and others.
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

package okio.internal

import java.security.MessageDigest

// TODO refactor to typealias after this is resolved https://youtrack.jetbrains.com/issue/KT-37316
internal actual fun newHashFunction(algorithm: String) = object : HashFunction {

  private val digest = MessageDigest.getInstance(algorithm)

  override fun update(input: ByteArray, offset: Int, byteCount: Int) = digest.update(
    input,
    offset,
    byteCount
  )

  override fun digest() = digest.digest()
}