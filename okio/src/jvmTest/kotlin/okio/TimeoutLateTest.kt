/*
 * Copyright (C) 2014 Square, Inc.
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

import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import org.junit.Test

class TimeoutLateTest {

  class TestTimeout(
    val name: String,
    val thread: Thread = Thread.currentThread(),
  ) : AsyncTimeout() {
    override fun timedOut() {
      thread.interrupt()
    }

    override fun toString(): String {
      return name
    }
  }

  var recordingStart = 0L
  val testStart = System.nanoTime()

  fun sleepUntil(time: Long) {
    val currentElapsed = (System.nanoTime() - testStart)
    val targetElapsed = time - recordingStart

    val nanos = targetElapsed - currentElapsed
    val ms = nanos / 1_000_000L
    val ns = nanos - (ms * 1_000_000L)
    if (ms > 0L || nanos > 0) {
      Thread.sleep(ms, ns.toInt())
    }
  }

  /** Annoyingly this does what we want. */
  @Test
  @Throws(InterruptedException::class)
  fun perfectTest() {
    val T1 = TestTimeout("T1")
    T1.timeout(3000000000, TimeUnit.NANOSECONDS)

    val T2 = TestTimeout("T2")
    T2.timeout(10000000000, TimeUnit.NANOSECONDS)

    val T3 = TestTimeout("T3")
    T3.timeout(2000000000, TimeUnit.NANOSECONDS)

    val T4 = TestTimeout("T4")
    T4.timeout(10000000000, TimeUnit.NANOSECONDS)

    val T5 = TestTimeout("T5")
    T5.timeout(2000000000, TimeUnit.NANOSECONDS)

//    sleepUntil(0)
    T1.enter()
    sleepUntil(21_021_375)
    T2.enter()
    sleepUntil(43_177_042)
    T2.exit()
    sleepUntil(46_650_750)
    T2.enter()
    sleepUntil(50_056_459)
    T2.exit()
    sleepUntil(53_838_167)
    T3.enter()
    sleepUntil(81_087_334)
    T3.exit()
    sleepUntil(2_000_000_000)
    T4.enter()
    sleepUntil(2_169_520_000)
    T4.exit()
    sleepUntil(2_175_734_375)
    T4.enter()
    sleepUntil(2_182_283_250)
    T4.exit()
    sleepUntil(2_188_262_042)
    T5.enter()
    sleepUntil(2_195_482_917)
    T5.exit()
    assertFailsWith<InterruptedException> {
      sleepUntil(3_500_000_000)
    }
    T1.exit()
  }

  /** Annoyingly this does what we want. */
  @Test
  @Throws(InterruptedException::class)
  fun timesOutTooLate() {
    recordingStart = 50959151216125
    val T1 = TestTimeout("T1")
    T1.timeout(3000000000, TimeUnit.NANOSECONDS)

    val T2 = TestTimeout("T2")
    T2.timeout(10000000000, TimeUnit.NANOSECONDS)

    val T3 = TestTimeout("T3")
    T3.timeout(2000000000, TimeUnit.NANOSECONDS)

    val T4 = TestTimeout("T4")
    T4.timeout(10000000000, TimeUnit.NANOSECONDS)

    val T5 = TestTimeout("T5")
    T5.timeout(2000000000, TimeUnit.NANOSECONDS)

    sleepUntil(0)
    T1.enter()
    sleepUntil(11_033_958)
    T2.enter()
    sleepUntil(11_192_625)
    T2.exit()
    sleepUntil(11_234_625)
    T2.enter()
    sleepUntil(11_271_458)
    T2.exit()
    sleepUntil(11_434_583)
    T3.enter()
    sleepUntil(11_492_708)
//      null.calling()
    sleepUntil(11_535_166)
    T3.exit()
    sleepUntil(2_061_649_333)
    T4.enter()
    sleepUntil(2_061_880_250)
    T4.exit()
    sleepUntil(2_062_048_000)
    T4.enter()
    sleepUntil(2_062_135_208)
    T4.exit()
    sleepUntil(2_062_193_750)
    T5.enter()
    sleepUntil(2_062_497_333)
//      null.calling()
    sleepUntil(2_062_883_916)
    T5.exit()
    assertFailsWith<InterruptedException> {

      sleepUntil(4_075_340_083)
    }
    T1.exit()

    /*
    enter 50959151216125 3000000000 RealCall.timeout 3

    calling timedOut 50963218461625 on RealCall.timeout 3

     */
  }
}
