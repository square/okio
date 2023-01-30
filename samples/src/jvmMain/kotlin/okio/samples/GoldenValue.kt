/*
 * Copyright (C) 2018 Square, Inc.
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
package okio.samples

import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

class KotlinGoldenValue {
  fun run() {
    val point = Point(8.0, 15.0)
    val pointBytes = serialize(point)
    println(pointBytes.base64())
    val goldenBytes = (
      "rO0ABXNyACRva2lvLnNhbXBsZXMuS290bGluR29sZGVuVmFsdWUkUG9pbnRF9yaY7cJ9EwIAA" +
        "kQAAXhEAAF5eHBAIAAAAAAAAEAuAAAAAAAA"
      ).decodeBase64()!!
    val decoded = deserialize(goldenBytes) as Point
    assertEquals(point, decoded)
  }

  @Throws(IOException::class)
  private fun serialize(o: Any?): ByteString {
    val buffer = Buffer()
    ObjectOutputStream(buffer.outputStream()).use { objectOut ->
      objectOut.writeObject(o)
    }
    return buffer.readByteString()
  }

  @Throws(IOException::class, ClassNotFoundException::class)
  private fun deserialize(byteString: ByteString): Any? {
    val buffer = Buffer()
    buffer.write(byteString)
    ObjectInputStream(buffer.inputStream()).use { objectIn ->
      val result = objectIn.readObject()
      if (objectIn.read() != -1) throw IOException("Unconsumed bytes in stream")
      return result
    }
  }

  internal class Point(var x: Double, var y: Double) : Serializable

  private fun assertEquals(
    a: Point,
    b: Point,
  ) {
    if (a.x != b.x || a.y != b.y) throw AssertionError()
  }
}

fun main() {
  KotlinGoldenValue().run()
}
