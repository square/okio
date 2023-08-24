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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import platform.CoreFoundation.CFRunLoopStop
import platform.Foundation.NSData
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSOutputStream
import platform.Foundation.NSStream
import platform.Foundation.NSStreamDataWrittenToMemoryStreamKey
import platform.Foundation.NSStreamDelegateProtocol
import platform.Foundation.NSStreamEvent
import platform.Foundation.NSStreamEventHasSpaceAvailable
import platform.Foundation.NSStreamEventOpenCompleted
import platform.Foundation.NSStreamStatusClosed
import platform.Foundation.NSStreamStatusNotOpen
import platform.Foundation.NSStreamStatusOpen
import platform.Foundation.NSThread
import platform.darwin.NSObject
import platform.darwin.NSUInteger
import platform.posix.uint8_tVar

private fun NSOutputStream.write(vararg strings: String) {
  for (str in strings) {
    str.encodeToByteArray().apply {
      assertEquals(size, this.write(this@write))
    }
  }
}

@OptIn(UnsafeNumber::class)
class BufferedSinkNSOutputStreamTest {
  @Test
  fun multipleWrites() {
    val buffer = Buffer()
    buffer.outputStream().apply {
      open()
      write("hello", " ", "world")
      close()
    }
    assertEquals("hello world", buffer.readUtf8())

    RealBufferedSink(buffer).outputStream().apply {
      open()
      write("hello", " ", "real", " sink")
      close()
    }
    assertEquals("hello real sink", buffer.readUtf8())
  }

  @Test
  fun bufferOutputStream() {
    testOutputStream(Buffer(), "abc")
    testOutputStream(Buffer(), "a" + "b".repeat(Segment.SIZE * 2) + "c")
  }

  @Test
  fun realBufferedSinkOutputStream() {
    testOutputStream(RealBufferedSink(Buffer()), "abc")
    testOutputStream(RealBufferedSink(Buffer()), "a" + "b".repeat(Segment.SIZE * 2) + "c")
  }

  private fun testOutputStream(sink: BufferedSink, input: String) {
    val out = sink.outputStream()
    val byteArray = input.encodeToByteArray()
    val size: NSUInteger = input.length.convert()
    byteArray.usePinned {
      val cPtr = it.addressOf(0).reinterpret<uint8_tVar>()

      assertEquals(NSStreamStatusNotOpen, out.streamStatus)
      assertEquals(-1, out.write(cPtr, size))
      out.open()
      assertEquals(NSStreamStatusOpen, out.streamStatus)

      assertEquals(size.convert(), out.write(cPtr, size))
      sink.flush()
      when (sink) {
        is Buffer -> {
          val data = out.propertyForKey(NSStreamDataWrittenToMemoryStreamKey) as NSData
          assertContentEquals(byteArray, data.toByteArray())
          assertContentEquals(byteArray, sink.buffer.readByteArray())
        }
        is RealBufferedSink -> assertContentEquals(byteArray, (sink.sink as Buffer).readByteArray())
      }
    }
  }

  @Test
  fun nsOutputStreamClose() {
    val buffer = Buffer()
    val sink = RealBufferedSink(buffer)
    assertFalse(sink.closed)

    val out = sink.outputStream()
    out.open()
    out.close()
    assertTrue(sink.closed)
    assertEquals(NSStreamStatusClosed, out.streamStatus)

    val byteArray = ByteArray(4)
    byteArray.usePinned {
      val cPtr = it.addressOf(0).reinterpret<uint8_tVar>()

      assertEquals(-1, out.write(cPtr, 4U))
      assertNull(out.streamError)
      assertTrue(sink.buffer.readByteArray().isEmpty())
    }
  }

  @Test
  fun delegateTest() {
    val runLoop = startRunLoop()

    fun produceWithDelegate(out: NSOutputStream, data: String) {
      val opened = Mutex(true)
      val written = atomic(0)
      val completed = Mutex(true)

      out.delegate = object : NSObject(), NSStreamDelegateProtocol {
        val source = data.encodeToByteArray()
        override fun stream(aStream: NSStream, handleEvent: NSStreamEvent) {
          assertEquals("run-loop", NSThread.currentThread.name)
          when (handleEvent) {
            NSStreamEventOpenCompleted -> opened.unlock()
            NSStreamEventHasSpaceAvailable -> {
              if (source.isNotEmpty()) {
                source.usePinned {
                  assertEquals(
                    data.length.convert(),
                    out.write(it.addressOf(written.value).reinterpret(), data.length.convert()),
                  )
                  written.value += data.length
                }
              }
              val writtenData = out.propertyForKey(NSStreamDataWrittenToMemoryStreamKey) as NSData
              assertEquals(data, writtenData.toByteArray().decodeToString())
              out.close()
              completed.unlock()
            }
            else -> fail("unexpected event ${handleEvent.asString()}")
          }
        }
      }
      out.scheduleInRunLoop(runLoop, NSDefaultRunLoopMode)
      out.open()
      runBlocking {
        opened.lockWithTimeout()
        completed.lockWithTimeout()
      }
      assertEquals(data.length, written.value)
    }

    produceWithDelegate(Buffer().outputStream(), "custom")
    produceWithDelegate(Buffer().outputStream(), "")
    CFRunLoopStop(runLoop.getCFRunLoop())
  }

  @Test
  fun testSubscribeAfterOpen() {
    val runLoop = startRunLoop()

    fun subscribeAfterOpen(out: NSOutputStream) {
      val available = Mutex(true)

      out.delegate = object : NSObject(), NSStreamDelegateProtocol {
        override fun stream(aStream: NSStream, handleEvent: NSStreamEvent) {
          assertEquals("run-loop", NSThread.currentThread.name)
          when (handleEvent) {
            NSStreamEventOpenCompleted -> fail("opened before subscribe")
            NSStreamEventHasSpaceAvailable -> available.unlock()
            else -> fail("unexpected event ${handleEvent.asString()}")
          }
        }
      }
      out.open()
      out.scheduleInRunLoop(runLoop, NSDefaultRunLoopMode)
      runBlocking {
        available.lockWithTimeout()
      }
      out.close()
    }

    subscribeAfterOpen(Buffer().outputStream())
    CFRunLoopStop(runLoop.getCFRunLoop())
  }
}
