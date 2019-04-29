// ktlint-disable filename
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
package okio

object `-DeprecatedUtf8` {
  @Deprecated(
      message = "moved to extension function",
      replaceWith = ReplaceWith(
          expression = "string.utf8Size()",
          imports = ["okio.utf8Size"]),
      level = DeprecationLevel.ERROR)
  fun size(string: String) = string.utf8Size()

  @Deprecated(
      message = "moved to extension function",
      replaceWith = ReplaceWith(
          expression = "string.utf8Size(beginIndex, endIndex)",
          imports = ["okio.utf8Size"]),
      level = DeprecationLevel.ERROR)
  fun size(string: String, beginIndex: Int, endIndex: Int) = string.utf8Size(beginIndex, endIndex)
}
