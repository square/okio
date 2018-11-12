package okio

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HybridMutexTest {

  private val mutex = HybridMutex()

  @Test fun lockSuspending_doesntBlock_whenNoContention() {
    runBlocking {
      val lockingJob = launch {
        mutex.lockSuspending()
      }

      yield()
      assertTrue(lockingJob.isCompleted)
    }
  }

  @Test fun lockBlocking_doesntBlock_whenNoContention() {
    val lockingThread = Thread {
      mutex.lockBlocking()
    }.apply { start() }

    lockingThread.join(100)
    assertFalse(lockingThread.isAlive)
  }

  @Test fun lockSuspending_blocks_whenLocked() {
    runBlocking {
      mutex.lockSuspending()
      val lockingJob = launch {
        mutex.lockSuspending()
      }

      yield()
      assertFalse(lockingJob.isCompleted)

      mutex.unlock()
      yield()
      assertTrue(lockingJob.isCompleted)
    }
  }
}
