package okio

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import platform.windows.CloseHandle
import platform.windows.CreateMutexA
import platform.windows.INFINITE
import platform.windows.ReleaseMutex
import platform.windows.WaitForSingleObject

actual class Lock {
  val mutex = CreateMutexA(
    null,
    0,
    null
  ) ?: throw lastErrorToIOException()

  @Suppress("unused")
  @OptIn(ExperimentalNativeApi::class)
  private val cleaner = createCleaner(mutex) {
    CloseHandle(mutex)
  }
}

internal actual fun newLock(): Lock = Lock()

actual inline fun <T> Lock.withLock(action: () -> T): T {
  contract {
    callsInPlace(action, InvocationKind.EXACTLY_ONCE)
  }

  try {
    WaitForSingleObject(mutex, INFINITE)
    return action()
  } finally {
    ReleaseMutex(mutex)
  }
}
