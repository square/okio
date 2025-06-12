// ktlint-disable filename
/*
 * Copyright (C) 2025 Square, Inc.
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
package okio.internal

import java.io.IOException
import java.net.Socket as JavaNetSocket
import java.net.SocketTimeoutException
import java.util.logging.Level
import java.util.logging.Logger
import okio.AsyncTimeout

private val logger = Logger.getLogger("okio.Okio")

internal class SocketAsyncTimeout(private val socket: JavaNetSocket) : AsyncTimeout() {
  override fun newTimeoutException(cause: IOException?): IOException {
    val ioe = SocketTimeoutException("timeout")
    if (cause != null) {
      ioe.initCause(cause)
    }
    return ioe
  }

  override fun timedOut() {
    try {
      socket.close()
    } catch (e: Exception) {
      logger.log(Level.WARNING, "Failed to close timed out socket $socket", e)
    } catch (e: AssertionError) {
      if (e.isAndroidGetsocknameError) {
        // Catch this exception due to a Firmware issue up to android 4.2.2
        // https://code.google.com/p/android/issues/detail?id=54072
        logger.log(Level.WARNING, "Failed to close timed out socket $socket", e)
      } else {
        throw e
      }
    }
  }
}

/**
 * Returns true if this error is due to a firmware bug fixed after Android 4.2.2.
 * https://code.google.com/p/android/issues/detail?id=54072
 */
internal val AssertionError.isAndroidGetsocknameError: Boolean get() {
  return cause != null && message?.contains("getsockname failed") ?: false
}
