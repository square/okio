// ktlint-disable filename
/*
 * Copyright (C) 2024 Square, Inc.
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
package okio.internal

import java.util.Calendar
import java.util.GregorianCalendar

internal actual val DEFAULT_COMPRESSION = java.util.zip.Deflater.DEFAULT_COMPRESSION

internal actual typealias CRC32 = java.util.zip.CRC32

internal actual fun datePartsToEpochMillis(
  year: Int,
  month: Int,
  day: Int,
  hour: Int,
  minute: Int,
  second: Int,
): Long {
  val calendar = GregorianCalendar()
  calendar.set(Calendar.MILLISECOND, 0)
  calendar.set(year, month - 1, day, hour, minute, second)
  return calendar.time.time
}
