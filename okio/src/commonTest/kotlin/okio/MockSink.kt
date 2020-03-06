/*
 * Copyright (C) 2014 Square, Inc.
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

import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A scriptable sink. Like Mockito, but worse and requiring less configuration.  */
class MockSink : Sink {
  private val log = mutableListOf<String>()
  private val callThrows = mutableMapOf<Int, IOException>()

  fun assertLog(vararg messages: String) {
    assertEquals(messages.toList(), log)
  }

  fun assertLogContains(message: String) {
    assertTrue(message in log)
  }

  fun scheduleThrow(call: Int, e: IOException) {
    callThrows[call] = e
  }

  private fun throwIfScheduled() {
    val exception = callThrows[log.size - 1]
    if (exception != null) throw exception
  }

  override fun write(source: Buffer, byteCount: Long) {
    log.add("write($source, $byteCount)")
    source.skip(byteCount)
    throwIfScheduled()
  }

  override fun flush() {
    log.add("flush()")
    throwIfScheduled()
  }

  override fun timeout(): Timeout {
    log.add("timeout()")
    return Timeout.NONE
  }

  override fun close() {
    log.add("close()")
    throwIfScheduled()
  }
}
