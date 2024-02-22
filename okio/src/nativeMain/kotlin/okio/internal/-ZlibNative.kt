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

internal actual val DEFAULT_COMPRESSION: Int = platform.zlib.Z_DEFAULT_COMPRESSION

/**
 * Roll our own date math because Kotlin doesn't include a built-in date math API, and the
 * kotlinx.datetime library doesn't offer a stable release at this time.
 *
 * Also, we don't necessarily want to take on that dependency for Okio.
 *
 * This implementation assumes UTC.
 *
 * This code is broken for years before 1970. It doesn't implement subtraction for leap years.
 *
 * This code is broken for out-of-range values. For example, it doesn't correctly implement leap
 * year offsets when the month is -24 or when the day is -365.
 */
internal actual fun datePartsToEpochMillis(
  year: Int,
  month: Int,
  day: Int,
  hour: Int,
  minute: Int,
  second: Int,
): Long {
  // Make sure month is in 1..12, adding or subtracting years as necessary.
  val rawMonth = month
  val month = (month - 1).mod(12) + 1
  val year = year + (rawMonth - month) / 12

  // Start with the cumulative number of days elapsed preceding the current year.
  var dayCount = (year - 1970) * 365L

  // Adjust by leap years. Years that divide 4 are leap years, unless they divide 100 but not 400.
  val leapYear = if (month > 2) year else year - 1
  dayCount += (leapYear - 1968) / 4 - (leapYear - 1900) / 100 + (leapYear - 1600) / 400

  // Add the cumulative number of days elapsed preceding the current month.
  dayCount += when (month) {
    1 -> 0
    2 -> 31
    3 -> 59
    4 -> 90
    5 -> 120
    6 -> 151
    7 -> 181
    8 -> 212
    9 -> 243
    10 -> 273
    11 -> 304
    else -> 334
  }

  // Add the cumulative number of days that precede the current day.
  dayCount += (day - 1)

  // Add hours + minutes + seconds for the current day.
  val hourCount = dayCount * 24 + hour
  val minuteCount = hourCount * 60 + minute
  val secondCount = minuteCount * 60 + second
  return secondCount * 1_000L
}
