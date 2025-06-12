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

import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.thread

/**
 * Queues reads and writes onto a background thread. This prevents potential deadlock if such
 * operations were done on the caller's thread.
 */
class AsyncSocket(
  socket: Socket,
) : Closeable {
  private val sink = socket.sink.buffer()
  private val source = socket.source.buffer()
  private val tasks = LinkedBlockingDeque<Task>()
  private val taskThread = thread(name = "AsyncSocket($socket)") {
    while (true) {
      val task = tasks.take()
      when (task) {
        is Task.Read -> {
          task.future.complete(source.readUtf8LineStrict())
        }
        is Task.Write -> {
          sink.writeUtf8("${task.string}\n")
          sink.flush()
        }
        Task.CloseSource -> {
          source.close()
        }
        Task.CloseSink -> {
          sink.close()
        }
        Task.Close -> {
          source.close()
          sink.close()
          break
        }
      }
    }
  }

  fun read(): String {
    val future = CompletableFuture<String>()
    tasks += Task.Read(future)
    return future.get()
  }

  fun write(string: String) {
    tasks += Task.Write(string)
  }

  fun closeSource() {
    tasks += Task.CloseSource
  }

  fun closeSink() {
    tasks += Task.CloseSink
  }

  override fun close() {
    tasks += Task.Close
    taskThread.join()
  }

  private sealed interface Task {
    data class Write(val string: String) : Task
    data class Read(val future: CompletableFuture<String>) : Task
    data object CloseSink : Task
    data object CloseSource : Task
    data object Close : Task
  }
}
