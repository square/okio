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

import java.util.concurrent.TimeUnit;

/** A byte count expressed over a period of time for determining transfer rates. */
public final class ByteRate {
  public static ByteRate bytesPerPeriod(long byteCount, long periodAmount, TimeUnit periodUnit) {
    ByteRate rate = new ByteRate();
    rate.setByteCount(byteCount);
    rate.setPeriod(periodAmount, periodUnit);
    return rate;
  }

  private volatile long byteCount = Long.MAX_VALUE;
  private volatile long periodAmount = 0L;
  private volatile TimeUnit periodUnit = TimeUnit.SECONDS;

  /** The byte count for the rate. */
  public long byteCount() {
    return byteCount;
  }

  /** A new byte count value for the next byte transfer. */
  public void setByteCount(long byteCount) {
    if (byteCount <= 0) throw new IllegalArgumentException("byteCount <= 0: " + byteCount);
    this.byteCount = byteCount;
  }

  /** The period of time in {@code unit} for the rate. */
  public long period(TimeUnit unit) {
    if (unit == null) throw new NullPointerException("unit == null");
    return unit.convert(periodAmount, periodUnit);
  }

  /** A new period of time for the next byte transfer. */
  public void setPeriod(long periodAmount, TimeUnit periodUnit) {
    if (periodAmount < 0) throw new IllegalArgumentException("periodAmount < 0: " + periodAmount);
    if (periodUnit == null) throw new NullPointerException("periodUnit == null");
    this.periodAmount = periodAmount;
    this.periodUnit = periodUnit;
  }

  /**
   * Return a period length in {@code periodUnit} units for {@code byteCount} for this transfer
   * rate.
   *
   * <p>For example, on a transfer rate of 50 bytes per 1 second this method would return 2 for
   * 100 bytes (also in seconds).
   * <pre>@{code
   * TransferRate fiftyBps = TransferRate.bytesPerPeriod(50, 1, SECONDS);
   * long p = fiftyBps.periodForByteCount(SECONDS, 100); // 2
   * }</pre>
   */
  public long periodForByteCount(TimeUnit periodUnit, long byteCount) {
    if (periodUnit == null) throw new NullPointerException("periodUnit == null");
    if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
    return periodUnit.convert(periodAmount * byteCount / this.byteCount, this.periodUnit);
  }
}
