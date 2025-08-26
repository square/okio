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

import app.cash.burst.TestFunction
import app.cash.burst.TestInterceptor

/**
 * A temporary directory on [fileSystem] that's usable for the current test.
 */
class TestDirectory(
  val fileSystem: FileSystem,
  val temporaryDirectory: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
) : TestInterceptor {
  lateinit var path: Path
    private set

  override fun intercept(testFunction: TestFunction) {
    path = temporaryDirectory / "${testFunction.functionName}-${randomToken(16)}"
    fileSystem.createDirectories(path)
    testFunction()
  }
}
