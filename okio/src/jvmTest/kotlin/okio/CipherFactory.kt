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
package okio

import javax.crypto.Cipher

class CipherFactory(
  private val transformation: String,
  private val init: Cipher.(mode: Int) -> Unit,
) {
  val blockSize
    get() = newCipher().blockSize

  val encrypt: Cipher
    get() = create(Cipher.ENCRYPT_MODE)

  val decrypt: Cipher
    get() = create(Cipher.DECRYPT_MODE)

  private fun newCipher(): Cipher = Cipher.getInstance(transformation)

  private fun create(mode: Int): Cipher = newCipher().apply { init(mode) }
}
