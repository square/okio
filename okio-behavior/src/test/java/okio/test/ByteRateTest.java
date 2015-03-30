/*
 * Copyright (C) 2015 Square, Inc.
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
package okio.test;

import org.junit.Test;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class ByteRateTest {
  @Test public void setByteCountInvalidArgumentThrows() {
    ByteRate rate = new ByteRate();
    try {
      rate.setByteCount(-1);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("byteCount <= 0: -1", e.getMessage());
    }
  }

  @Test public void byteCountReflectsChanges() {
    ByteRate rate = ByteRate.bytesPerPeriod(100L, 1L, SECONDS);
    assertEquals(100L, rate.byteCount());
    rate.setByteCount(10L);
    assertEquals(10L, rate.byteCount());
    rate.setByteCount(1000L);
    assertEquals(1000L, rate.byteCount());
  }

  @Test public void setPeriodInvalidArgumentThrows() {
    ByteRate rate = new ByteRate();
    try {
      rate.setPeriod(-1L, SECONDS);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("periodAmount < 0: -1", e.getMessage());
    }
    try {
      rate.setPeriod(1L, null);
      fail();
    } catch (NullPointerException e) {
      assertEquals("periodUnit == null", e.getMessage());
    }
  }

  @Test public void periodConverts() {
    ByteRate rate = ByteRate.bytesPerPeriod(1L, 1000000000000000000L, NANOSECONDS);
    assertEquals(1000000000000000000L, rate.period(NANOSECONDS));
    assertEquals(1000000000000000L, rate.period(MICROSECONDS));
    assertEquals(1000000000000L, rate.period(MILLISECONDS));
    assertEquals(1000000000L, rate.period(SECONDS));
    assertEquals(16666666, rate.period(MINUTES));
    assertEquals(277777, rate.period(HOURS));
    assertEquals(11574, rate.period(DAYS));
  }

  @Test public void periodInvalidArgumentThrows() {
    ByteRate rate = new ByteRate();
    try {
      rate.period(null);
      fail();
    } catch (NullPointerException e) {
      assertEquals("unit == null", e.getMessage());
    }
  }

  @Test public void periodForByteCountConverts() {
    ByteRate rate = ByteRate.bytesPerPeriod(1000L, 1000L, MILLISECONDS);
    assertEquals(2000L, rate.periodForByteCount(MILLISECONDS, 2000L));
    assertEquals(2L, rate.periodForByteCount(SECONDS, 2000L));
    assertEquals(500L, rate.periodForByteCount(MILLISECONDS, 500L));
    assertEquals(0, rate.periodForByteCount(SECONDS, 500L));
  }

  @Test public void periodForByteCountInvalidArgumentThrows() {
    ByteRate rate = new ByteRate();
    try {
      rate.periodForByteCount(null, 1L);
      fail();
    } catch (NullPointerException e) {
      assertEquals("periodUnit == null", e.getMessage());
    }
    try {
      rate.periodForByteCount(SECONDS, -1L);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("byteCount < 0: -1", e.getMessage());
    }
  }
}
