/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

@file:JvmName("-Base64")
package okio

import okio.ByteString.Companion.encodeUtf8
import kotlin.jvm.JvmName

/** @author Alexander Y. Kleymenov */

internal val BASE64 =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".encodeUtf8().data
internal val BASE64_URL_SAFE =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".encodeUtf8().data

internal fun String.decodeBase64ToArray(): ByteArray? {
  // Ignore trailing '=' padding and whitespace from the input.
  var limit = length
  while (limit > 0) {
    val c = this[limit - 1]
    if (c != '=' && c != '\n' && c != '\r' && c != ' ' && c != '\t') {
      break
    }
    limit--
  }

  // If the input includes whitespace, this output array will be longer than necessary.
  val out = ByteArray((limit * 6L / 8L).toInt())
  var outCount = 0
  var inCount = 0

  var word = 0
  for (pos in 0 until limit) {
    val c = this[pos]

    val bits: Int
    if (c in 'A'..'Z') {
      // char ASCII value
      //  A    65    0
      //  Z    90    25 (ASCII - 65)
      bits = c.toInt() - 65
    } else if (c in 'a'..'z') {
      // char ASCII value
      //  a    97    26
      //  z    122   51 (ASCII - 71)
      bits = c.toInt() - 71
    } else if (c in '0'..'9') {
      // char ASCII value
      //  0    48    52
      //  9    57    61 (ASCII + 4)
      bits = c.toInt() + 4
    } else if (c == '+' || c == '-') {
      bits = 62
    } else if (c == '/' || c == '_') {
      bits = 63
    } else if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
      continue
    } else {
      return null
    }

    // Append this char's 6 bits to the word.
    word = word shl 6 or bits

    // For every 4 chars of input, we accumulate 24 bits of output. Emit 3 bytes.
    inCount++
    if (inCount % 4 == 0) {
      out[outCount++] = (word shr 16).toByte()
      out[outCount++] = (word shr 8).toByte()
      out[outCount++] = word.toByte()
    }
  }

  val lastWordChars = inCount % 4
  when (lastWordChars) {
    1 -> {
      // We read 1 char followed by "===". But 6 bits is a truncated byte! Fail.
      return null
    }
    2 -> {
      // We read 2 chars followed by "==". Emit 1 byte with 8 of those 12 bits.
      word = word shl 12
      out[outCount++] = (word shr 16).toByte()
    }
    3 -> {
      // We read 3 chars, followed by "=". Emit 2 bytes for 16 of those 18 bits.
      word = word shl 6
      out[outCount++] = (word shr 16).toByte()
      out[outCount++] = (word shr 8).toByte()
    }
  }

  // If we sized our out array perfectly, we're done.
  if (outCount == out.size) return out

  // Copy the decoded bytes to a new, right-sized array.
  return out.copyOf(outCount)
}

internal fun ByteArray.encodeBase64(map: ByteArray = BASE64): String {
  val length = (size + 2) / 3 * 4
  val out = ByteArray(length)
  var index = 0
  val end = size - size % 3
  var i = 0
  while (i < end) {
    val b0 = this[i++].toInt()
    val b1 = this[i++].toInt()
    val b2 = this[i++].toInt()
    out[index++] = map[(b0 and 0xff shr 2)]
    out[index++] = map[(b0 and 0x03 shl 4) or (b1 and 0xff shr 4)]
    out[index++] = map[(b1 and 0x0f shl 2) or (b2 and 0xff shr 6)]
    out[index++] = map[(b2 and 0x3f)]
  }
  when (size - end) {
    1 -> {
      val b0 = this[i].toInt()
      out[index++] = map[b0 and 0xff shr 2]
      out[index++] = map[b0 and 0x03 shl 4]
      out[index++] = '='.toByte()
      out[index] = '='.toByte()
    }
    2 -> {
      val b0 = this[i++].toInt()
      val b1 = this[i].toInt()
      out[index++] = map[(b0 and 0xff shr 2)]
      out[index++] = map[(b0 and 0x03 shl 4) or (b1 and 0xff shr 4)]
      out[index++] = map[(b1 and 0x0f shl 2)]
      out[index] = '='.toByte()
    }
  }
  return out.toUtf8String()
}
