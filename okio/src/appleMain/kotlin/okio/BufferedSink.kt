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

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import platform.Foundation.NSError
import platform.Foundation.NSOutputStream
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopMode
import platform.Foundation.NSStream
import platform.Foundation.NSStreamDataWrittenToMemoryStreamKey
import platform.Foundation.NSStreamDelegateProtocol
import platform.Foundation.NSStreamEvent
import platform.Foundation.NSStreamEventErrorOccurred
import platform.Foundation.NSStreamEventHasSpaceAvailable
import platform.Foundation.NSStreamEventOpenCompleted
import platform.Foundation.NSStreamPropertyKey
import platform.Foundation.NSStreamStatusClosed
import platform.Foundation.NSStreamStatusError
import platform.Foundation.NSStreamStatusNotOpen
import platform.Foundation.NSStreamStatusOpen
import platform.Foundation.NSStreamStatusOpening
import platform.Foundation.NSStreamStatusWriting
import platform.Foundation.performInModes
import platform.darwin.NSInteger
import platform.darwin.NSUInteger
import platform.posix.uint8_tVar

/**
 * Returns an output stream that writes to this sink. Closing the stream will also close this sink.
 */
fun BufferedSink.outputStream(): NSOutputStream = BufferedSinkNSOutputStream(this)

@OptIn(UnsafeNumber::class, ExperimentalNativeApi::class)
private class BufferedSinkNSOutputStream(
  private val sink: BufferedSink,
) : NSOutputStream(toMemory = Unit), NSStreamDelegateProtocol {

  private val isClosed: () -> Boolean = when (sink) {
    is RealBufferedSink -> sink::closed
    is Buffer -> {
      { false }
    }
  }

  private var status = NSStreamStatusNotOpen
  private var error: NSError? = null
    set(value) {
      status = NSStreamStatusError
      field = value
      postEvent(NSStreamEventErrorOccurred)
      sink.close()
    }

  override fun streamStatus() = if (status != NSStreamStatusError && isClosed()) NSStreamStatusClosed else status

  override fun streamError() = error

  override fun open() {
    if (status == NSStreamStatusNotOpen) {
      status = NSStreamStatusOpening
      status = NSStreamStatusOpen
      postEvent(NSStreamEventOpenCompleted)
      postEvent(NSStreamEventHasSpaceAvailable)
    }
  }

  override fun close() {
    if (status == NSStreamStatusError || status == NSStreamStatusNotOpen) return
    status = NSStreamStatusClosed
    runLoop = null
    runLoopModes = listOf()
    sink.close()
  }

  override fun write(buffer: CPointer<uint8_tVar>?, maxLength: NSUInteger): NSInteger {
    if (streamStatus != NSStreamStatusOpen || buffer == null) return -1
    status = NSStreamStatusWriting
    val toWrite = minOf(maxLength, Int.MAX_VALUE.convert()).toInt()
    return try {
      sink.buffer.write(buffer, toWrite)
      sink.emitCompleteSegments()
      status = NSStreamStatusOpen
      toWrite.convert()
    } catch (e: Exception) {
      error = e.toNSError()
      -1
    }
  }

  override fun hasSpaceAvailable() = !isFinished

  private val isFinished
    get() = when (streamStatus) {
      NSStreamStatusClosed, NSStreamStatusError -> true
      else -> false
    }

  override fun propertyForKey(key: NSStreamPropertyKey): Any? = when (key) {
    NSStreamDataWrittenToMemoryStreamKey -> sink.buffer.snapshotAsNSData()
    else -> null
  }

  override fun setProperty(property: Any?, forKey: NSStreamPropertyKey) = false

  // WeakReference as delegate should not be retained
  // https://developer.apple.com/documentation/foundation/nsstream/1418423-delegate
  private var _delegate: WeakReference<NSStreamDelegateProtocol>? = null
  private var runLoop: NSRunLoop? = null
  private var runLoopModes = listOf<NSRunLoopMode>()

  private fun postEvent(event: NSStreamEvent) {
    val runLoop = runLoop ?: return
    runLoop.performInModes(runLoopModes) {
      if (runLoop == this.runLoop) {
        delegateOrSelf.stream(this, event)
      }
    }
  }

  override fun delegate() = _delegate?.value

  private val delegateOrSelf get() = delegate ?: this

  override fun setDelegate(delegate: NSStreamDelegateProtocol?) {
    _delegate = delegate?.let { WeakReference(it) }
  }

  override fun stream(aStream: NSStream, handleEvent: NSStreamEvent) {
    // no-op
  }

  override fun scheduleInRunLoop(aRunLoop: NSRunLoop, forMode: NSRunLoopMode) {
    if (runLoop == null) {
      runLoop = aRunLoop
    }
    if (runLoop == aRunLoop) {
      runLoopModes += forMode
    }
    if (status == NSStreamStatusOpen) {
      postEvent(NSStreamEventHasSpaceAvailable)
    }
  }

  override fun removeFromRunLoop(aRunLoop: NSRunLoop, forMode: NSRunLoopMode) {
    if (aRunLoop == runLoop) {
      runLoopModes -= forMode
      if (runLoopModes.isEmpty()) {
        runLoop = null
      }
    }
  }

  override fun description() = "$sink.outputStream()"
}
