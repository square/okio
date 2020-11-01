/*
 * Copyright (C) 2020 Square, Inc.
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

abstract class Filesystem {
  /**
   * The current process's working directory. This is the result of the `getcwd` command on POSIX
   * and the `user.dir` System property in Java. This is an absolute path that all relative paths
   * are resolved against when using this filesystem.
   *
   * @throws IOException if the current process doesn't have access to the current working
   *     directory, if it's been deleted since the current process started, or there is another
   *     failure accessing the current working directory.
   */
  @Throws(IOException::class)
  abstract fun cwd(): Path

  companion object {
    /**
     * The current process's host filesystem. Use this instance directly, or dependency inject a
     * [Filesystem] to make code testable.
     */
    val SYSTEM: Filesystem = PLATFORM_FILESYSTEM
  }
}
