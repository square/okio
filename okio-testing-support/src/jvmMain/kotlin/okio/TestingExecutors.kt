/*
 * Copyright (C) 2023 Block, Inc.
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

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory

object TestingExecutors {
  val isLoom = System.getProperty("loomEnabled").toBoolean()

  fun newScheduledExecutorService(corePoolSize: Int = 0): ScheduledExecutorService = if (isLoom) {
    Executors.newScheduledThreadPool(corePoolSize, newVirtualThreadFactory())
  } else {
    Executors.newScheduledThreadPool(corePoolSize)
  }

  fun newExecutorService(corePoolSize: Int = 0): ExecutorService = if (isLoom) {
    Executors.newScheduledThreadPool(corePoolSize, newVirtualThreadFactory())
  } else {
    Executors.newScheduledThreadPool(corePoolSize)
  }

  fun newVirtualThreadFactory(): ThreadFactory {
    val threadBuilder = Thread::class.java.getMethod("ofVirtual").invoke(null)
    return Class.forName("java.lang.Thread\$Builder").getMethod("factory").invoke(threadBuilder) as ThreadFactory
  }

  fun newVirtualThreadPerTaskExecutor(): ExecutorService {
    return Executors::class.java.getMethod("newVirtualThreadPerTaskExecutor").invoke(null) as ExecutorService
  }
}
