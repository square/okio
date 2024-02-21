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

import kotlin.test.Test
import kotlin.test.assertEquals
import okio.withUtc

class DatePartsToEpochMillisTest {
  /**
   * Test every day from 1970-01-01 (epochMillis = 0) until 2200-01-01. Note that this includes the
   * full range of ZIP DOS dates (1980-01-01 until 2107-12-31).
   */
  @Test
  fun everySingleDay() {
    val dateTester = DateTester()
    while (dateTester.year < 2200) {
      dateTester.addDay()
      dateTester.check()
    }
  }

  /** Test the boundaries of the ZIP DOS date format. */
  @Test
  fun dosDateRange() {
    assertEquals(
      (365 * 10 + 2) * (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(year = 1980, month = 1, day = 1),
    )
    assertEquals(
      (365 * 138 + 33) * (24 * 60 * 60 * 1000L) - 1_000L,
      datePartsToEpochMillisUtc(
        year = 2107,
        month = 12,
        day = 31,
        hour = 23,
        minute = 59,
        second = 59,
      ),
    )
  }

  @Test
  fun monthOutOfBounds() {
    // Month -21 is the same as March, 22 months ago.
    assertEquals(
      (-365 + -365 + 31 + 28) * (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(month = -21, day = 1),
    )

    // Month -12 is the same as December, 13 months ago.
    assertEquals(
      (-365 + -31) * (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(year = 1970, month = -12, day = 1),
    )

    // Month -11 is the same as January, 12 months ago.
    assertEquals(
      -365 * (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(year = 1970, month = -11, day = 1),
    )

    // Month -1 is the same as November, 2 months ago.
    assertEquals(
      (-31 + -30) * (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(year = 1970, month = -1, day = 1),
    )

    // Month 0 is the same as December, 1 month ago.
    assertEquals(
      -31 * (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(year = 1970, month = 0, day = 1),
    )

    // Month 13 is the same as January, 12 months from now.
    assertEquals(
      365 * (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(year = 1970, month = 13, day = 1),
    )

    // Month 24 is the same as December, 23 months from now
    assertEquals(
      (365 + 365 - 31) * (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(year = 1970, month = 24, day = 1),
    )

    // Month 25 is the same as January, 24 months from now
    assertEquals(
      (365 + 365) * (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(year = 1970, month = 25, day = 1),
    )
  }

  @Test
  fun dayOutOfBounds() {
    // Day -364 is the same as January 1 of the previous year.
    assertEquals(
      -365 * (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(year = 1970, month = 1, day = -364),
    )

    // Day -1 is the same as December 30 of the previous year.
    assertEquals(
      -2 * (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(year = 1970, month = 1, day = -1),
    )

    // Day 0 is the same as December 31 of the previous year.
    assertEquals(
      -1 * (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(year = 1970, month = 1, day = 0),
    )

    // Day 32 is the same as February 1.
    assertEquals(
      31 * (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(year = 1970, month = 1, day = 32),
    )

    // Day 33 is the same as February 2.
    assertEquals(
      32 * (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(year = 1970, month = 1, day = 33),
    )
  }

  @Test
  fun hourOutOfBounds() {
    assertEquals(
      (-24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(hour = -24),
    )
    assertEquals(
      (-1 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(hour = -1),
    )
    assertEquals(
      (24 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(hour = 24),
    )
    assertEquals(
      (25 * 60 * 60 * 1000L),
      datePartsToEpochMillisUtc(hour = 25),
    )
  }

  @Test
  fun minuteOutOfBounds() {
    assertEquals(
      (-1 * 60 * 1000L),
      datePartsToEpochMillisUtc(minute = -1),
    )
    assertEquals(
      (60 * 60 * 1000L),
      datePartsToEpochMillisUtc(minute = 60),
    )
    assertEquals(
      (61 * 60 * 1000L),
      datePartsToEpochMillisUtc(minute = 61),
    )
  }

  @Test
  fun secondOutOfBounds() {
    assertEquals(
      (-1 * 1000L),
      datePartsToEpochMillisUtc(hour = 0, second = -1),
    )
    assertEquals(
      (60 * 1000L),
      datePartsToEpochMillisUtc(hour = 0, second = 60),
    )
    assertEquals(
      (61 * 1000L),
      datePartsToEpochMillisUtc(hour = 0, second = 61),
    )
  }

  private class DateTester {
    var epochMillis = 0L
    var year = 1970
    var month = 1
    var day = 1

    fun addDay() {
      day++
      epochMillis += 24L * 60 * 60 * 1000

      val monthSize = when (month) {
        1 -> 31
        2 -> {
          when {
            year % 400 == 0 -> 29
            year % 100 == 0 -> 28
            year % 4 == 0 -> 29
            else -> 28
          }
        }

        3 -> 31
        4 -> 30
        5 -> 31
        6 -> 30
        7 -> 31
        8 -> 31
        9 -> 30
        10 -> 31
        11 -> 30
        12 -> 31
        else -> error("unexpected month $month")
      }

      if (day > monthSize) {
        day -= monthSize
        month++
        if (month > 12) {
          month -= 12
          year++
        }
      }
    }

    fun check() {
      assertEquals(
        expected = epochMillis,
        actual = datePartsToEpochMillisUtc(
          year = year,
          month = month,
          day = day,
        ),
        message = "y=$year m=$month d=$day",
      )
    }
  }
}

fun datePartsToEpochMillisUtc(
  year: Int = 1970,
  month: Int = 1,
  day: Int = 1,
  hour: Int = 0,
  minute: Int = 0,
  second: Int = 0,
): Long {
  return withUtc {
    datePartsToEpochMillis(year, month, day, hour, minute, second)
  }
}
