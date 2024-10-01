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

internal expect val DEFAULT_COMPRESSION: Int

/**
 * Note that this inherits the local time zone.
 *
 * @param year such as 1970 or 2024
 * @param month a value in the range 1 (January) through 12 (December).
 * @param day a value in the range 1 through 31.
 * @param hour a value in the range 0 through 23.
 * @param minute a value in the range 0 through 59.
 * @param second a value in the range 0 through 59.
 */
internal expect fun datePartsToEpochMillis(
  year: Int,
  month: Int,
  day: Int,
  hour: Int,
  minute: Int,
  second: Int,
): Long
