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
package okio

/**
 * A pair of streams for interactive communication with another machine.
 *
 * Send data to the peer by writing to [sink], and read data from the peer by reading from [source].
 *
 * This can be implemented by a plain TCP socket. It can also be layered to add features like
 * security (as in a TLS socket) or connectivity (as in a proxy socket).
 *
 * Closing the [source] does not impact the [sink], and vice versa.
 *
 * You must close the source and the sink to release the resources held by this socket. If you're using both from the
 * same thread, you can do that with nested `use` blocks:
 *
 * ```kotlin
 * socket.source.use { source ->
 *   socket.sink.use { sink ->
 *     readAndWrite(source, sink)
 *   }
 * }
 * ```
 */
interface Socket {
  val source: Source
  val sink: Sink

  /**
   * Fail any in-flight and future operations. After canceling:
   *
   *  * Any attempt to write or flush [sink] will fail immediately with an [IOException].
   *  * Any attempt to read [source] will fail immediately with an [IOException].
   *
   * Closing the source and the sink will complete normally even after a socket has been canceled.
   *
   * This operation may be called by any thread at any time. It is safe to call concurrently while
   * operating on the source or the sink.
   */
  fun cancel()
}
