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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import platform.CoreFoundation.CFRunLoopStop
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSInputStream
import platform.Foundation.NSStream
import platform.Foundation.NSStreamDelegateProtocol
import platform.Foundation.NSStreamEvent
import platform.Foundation.NSStreamEventEndEncountered
import platform.Foundation.NSStreamEventHasBytesAvailable
import platform.Foundation.NSStreamEventOpenCompleted
import platform.Foundation.NSStreamStatusClosed
import platform.Foundation.NSStreamStatusNotOpen
import platform.Foundation.NSStreamStatusOpen
import platform.Foundation.NSThread
import platform.darwin.NSObject
import platform.posix.uint8_tVar

@OptIn(UnsafeNumber::class)
class BufferedSourceInputStreamTest {
  @Test
  fun bufferInputStream() {
    val source = Buffer()
    source.writeUtf8("abc")
    testInputStream(source.inputStream())
  }

  @Test
  fun realBufferedSourceInputStream() {
    val source = Buffer()
    source.writeUtf8("abc")
    testInputStream(RealBufferedSource(source).inputStream())
  }

  private fun testInputStream(input: NSInputStream) {
    val byteArray = ByteArray(4)
    byteArray.usePinned {
      val cPtr = it.addressOf(0).reinterpret<uint8_tVar>()

      assertEquals(NSStreamStatusNotOpen, input.streamStatus)
      assertEquals(-1, input.read(cPtr, 4U))
      input.open()
      assertEquals(NSStreamStatusOpen, input.streamStatus)

      byteArray.fill(-5)
      assertEquals(3, input.read(cPtr, 4U))
      assertEquals("[97, 98, 99, -5]", byteArray.contentToString())

      byteArray.fill(-7)
      assertEquals(0, input.read(cPtr, 4U))
      assertEquals("[-7, -7, -7, -7]", byteArray.contentToString())
    }
  }

  @Test
  fun bufferInputStreamLongData() {
    val source = Buffer()
    source.writeUtf8("a" + "b".repeat(Segment.SIZE * 2) + "c")
    testInputStreamLongData(source.inputStream())
  }

  @Test
  fun realBufferedSourceInputStreamLongData() {
    val source = Buffer()
    source.writeUtf8("a" + "b".repeat(Segment.SIZE * 2) + "c")
    testInputStreamLongData(RealBufferedSource(source).inputStream())
  }

  private fun testInputStreamLongData(input: NSInputStream) {
    val lengthPlusOne = Segment.SIZE * 2 + 3
    val byteArray = ByteArray(lengthPlusOne)
    byteArray.usePinned {
      val cPtr = it.addressOf(0).reinterpret<uint8_tVar>()

      assertEquals(NSStreamStatusNotOpen, input.streamStatus)
      assertEquals(-1, input.read(cPtr, lengthPlusOne.convert()))
      input.open()
      assertEquals(NSStreamStatusOpen, input.streamStatus)

      byteArray.fill(-5)
      assertEquals(Segment.SIZE.convert(), input.read(cPtr, lengthPlusOne.convert()))
      assertEquals("[97${", 98".repeat(Segment.SIZE - 1)}${", -5".repeat(Segment.SIZE + 3)}]", byteArray.contentToString())

      byteArray.fill(-6)
      assertEquals(Segment.SIZE.convert(), input.read(cPtr, lengthPlusOne.convert()))
      assertEquals("[98${", 98".repeat(Segment.SIZE - 1)}${", -6".repeat(Segment.SIZE + 3)}]", byteArray.contentToString())

      byteArray.fill(-7)
      assertEquals(2, input.read(cPtr, lengthPlusOne.convert()))
      assertEquals("[98, 99${", -7".repeat(Segment.SIZE * 2 + 1)}]", byteArray.contentToString())

      byteArray.fill(-8)
      assertEquals(0, input.read(cPtr, lengthPlusOne.convert()))
      assertEquals("[-8${", -8".repeat(lengthPlusOne - 1)}]", byteArray.contentToString())
    }
  }

  @Test
  fun nsInputStreamClose() {
    val buffer = Buffer()
    buffer.writeUtf8("abc")
    val source = RealBufferedSource(buffer)
    assertFalse(source.closed)

    val input = source.inputStream()
    input.open()
    input.close()
    assertTrue(source.closed)
    assertEquals(NSStreamStatusClosed, input.streamStatus)

    val byteArray = ByteArray(4)
    byteArray.usePinned {
      val cPtr = it.addressOf(0).reinterpret<uint8_tVar>()

      byteArray.fill(-5)
      assertEquals(-1, input.read(cPtr, 4U))
      assertNull(input.streamError)
      assertEquals("[-5, -5, -5, -5]", byteArray.contentToString())
    }
  }

  @Test
  fun delegateTest() {
    val runLoop = startRunLoop()

    fun consumeWithDelegate(input: NSInputStream, data: String) {
      val opened = Mutex(true)
      val read = atomic(0)
      val completed = Mutex(true)

      input.delegate = object : NSObject(), NSStreamDelegateProtocol {
        val sink = ByteArray(data.length)
        override fun stream(aStream: NSStream, handleEvent: NSStreamEvent) {
          assertEquals("run-loop", NSThread.currentThread.name)
          when (handleEvent) {
            NSStreamEventOpenCompleted -> opened.unlock()
            NSStreamEventHasBytesAvailable -> {
              sink.usePinned {
                assertEquals(1, input.read(it.addressOf(read.value).reinterpret(), 1U))
                read.value++
              }
            }
            NSStreamEventEndEncountered -> {
              assertEquals(data, sink.decodeToString())
              input.close()
              completed.unlock()
            }
            else -> fail("unexpected event ${handleEvent.asString()}")
          }
        }
      }
      input.scheduleInRunLoop(runLoop, NSDefaultRunLoopMode)
      input.open()
      runBlocking {
        opened.lockWithTimeout()
        completed.lockWithTimeout()
      }
      assertEquals(data.length, read.value)
    }

    consumeWithDelegate(Buffer().apply { writeUtf8("custom") }.inputStream(), "custom")
    consumeWithDelegate(Buffer().inputStream(), "")
    CFRunLoopStop(runLoop.getCFRunLoop())
  }

  @Test
  fun testRunLoopSwitch() {
    val runLoop1 = startRunLoop("run-loop-1")
    val runLoop2 = startRunLoop("run-loop-2")

    fun consumeSwitching(input: NSInputStream, data: String) {
      val opened = Mutex(true)
      val readLock = reentrantLock()
      var read = 0
      val completed = Mutex(true)

      input.delegate = object : NSObject(), NSStreamDelegateProtocol {
        val sink = ByteArray(data.length)
        override fun stream(aStream: NSStream, handleEvent: NSStreamEvent) {
          // Ensure thread safe access to `read` between scheduled run loops
          readLock.withLock {
            if (read == 0) {
              // until first read
              assertEquals("run-loop-1", NSThread.currentThread.name)
            } else {
              // after first read
              assertEquals("run-loop-2", NSThread.currentThread.name)
            }
            when (handleEvent) {
              NSStreamEventOpenCompleted -> opened.unlock()
              NSStreamEventHasBytesAvailable -> {
                if (read == 0) {
                  // switch to other run loop before first read
                  input.removeFromRunLoop(runLoop1, NSDefaultRunLoopMode)
                  input.scheduleInRunLoop(runLoop2, NSDefaultRunLoopMode)
                } else if (read >= data.length - 3) {
                  // unsubscribe before last read
                  input.removeFromRunLoop(runLoop2, NSDefaultRunLoopMode)
                }
                sink.usePinned {
                  val readBytes = input.read(it.addressOf(read).reinterpret(), 3U)
                  assertNotEquals(0, readBytes)
                  read += readBytes.toInt()
                }
                if (read == data.length) {
                  assertEquals(data, sink.decodeToString())
                  completed.unlock()
                }
              }
              NSStreamEventEndEncountered -> fail("$data shouldn't be subscribed")
              else -> fail("unexpected event ${handleEvent.asString()}")
            }
          }
        }
      }
      input.scheduleInRunLoop(runLoop1, NSDefaultRunLoopMode)
      input.open()
      runBlocking {
        opened.lockWithTimeout()
        completed.lockWithTimeout()
        // wait a bit to be sure delegate is no longer called
        delay(200)
      }
      input.close()
    }

    consumeSwitching(Buffer().apply { writeUtf8("custom") }.inputStream(), "custom")
    CFRunLoopStop(runLoop1.getCFRunLoop())
    CFRunLoopStop(runLoop2.getCFRunLoop())
  }

  @Test
  fun testSubscribeAfterOpen() {
    val runLoop = startRunLoop()

    fun subscribeAfterOpen(input: NSInputStream, data: String) {
      val available = Mutex(true)

      input.delegate = object : NSObject(), NSStreamDelegateProtocol {
        override fun stream(aStream: NSStream, handleEvent: NSStreamEvent) {
          assertEquals("run-loop", NSThread.currentThread.name)
          when (handleEvent) {
            NSStreamEventOpenCompleted -> fail("opened before subscribe")
            NSStreamEventHasBytesAvailable -> {
              val sink = ByteArray(data.length)
              sink.usePinned {
                assertEquals(data.length.convert(), input.read(it.addressOf(0).reinterpret(), data.length.convert()))
              }
              assertEquals(data, sink.decodeToString())
              input.close()
              available.unlock()
            }
            else -> fail("unexpected event ${handleEvent.asString()}")
          }
        }
      }
      input.open()
      input.scheduleInRunLoop(runLoop, NSDefaultRunLoopMode)
      runBlocking {
        available.lockWithTimeout()
      }
    }

    subscribeAfterOpen(Buffer().apply { writeUtf8("custom") }.inputStream(), "custom")
    CFRunLoopStop(runLoop.getCFRunLoop())
  }
}
