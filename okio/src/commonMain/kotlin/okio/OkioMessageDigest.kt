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

// TODO refactor to typealias after this is resolved https://youtrack.jetbrains.com/issue/KT-37316
internal interface OkioMessageDigest {

  /**
   * Update the digest using [input]
   */
  fun update(input: ByteArray)

  /**
   * Complete the hash calculaion and return the hash as a [ByteArray]
   */
  fun digest(): ByteArray
}

internal expect fun newMessageDigest(algorithm: String): OkioMessageDigest
