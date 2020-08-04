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

import okio.internal.MD5MessageDigest
import okio.internal.Sha1MessageDigest
import okio.internal.Sha256MessageDigest
import okio.internal.Sha512MessageDigest

internal actual fun newMessageDigest(
  algorithm: String
): OkioMessageDigest = when (algorithm) {
  "SHA-1" -> Sha1MessageDigest()
  "SHA-256" -> Sha256MessageDigest()
  "SHA-512" -> Sha512MessageDigest()
  "MD5" -> MD5MessageDigest()
  else -> throw IllegalArgumentException("$algorithm is not a hashing algorithm")
}
