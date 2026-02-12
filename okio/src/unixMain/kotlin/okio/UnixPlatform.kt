package okio

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.posix.*

actual class Lock {
  val mutex = nativeHeap.alloc<pthread_mutex_t>().apply {
    if (pthread_mutex_init(ptr, null) != 0) {
      throw errnoToIOException(errno)
    }
  }

  @Suppress("unused")
  @OptIn(ExperimentalNativeApi::class)
  private val cleaner = createCleaner(mutex) {
    pthread_mutex_destroy(it.ptr)
    nativeHeap.free(it)
  }
}

internal actual fun newLock(): Lock = Lock()

actual inline fun <T> Lock.withLock(action: () -> T): T {
  contract {
    callsInPlace(action, InvocationKind.EXACTLY_ONCE)
  }

  try {
    pthread_mutex_lock(mutex.ptr)
    return action()
  } finally {
    pthread_mutex_unlock(mutex.ptr)
  }
}
