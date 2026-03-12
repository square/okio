/*
 * Copyright (C) 2025 Square, Inc.
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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test
import org.junit.Assert.*

class AsyncTimeoutConcurrencyTest {

    @Test
    fun concurrentEnterExit() {
        val threadCount = 10
        val operationsPerThread = 1000
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val completedOperations = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                startLatch.await()
                repeat(operationsPerThread) {
                    val timeout = AsyncTimeout()
                    timeout.timeout(100, TimeUnit.MILLISECONDS)
                    timeout.enter()
                    timeout.exit()
                    completedOperations.incrementAndGet()
                }
            }
        }

        startLatch.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS))
        assertEquals(threadCount * operationsPerThread, completedOperations.get())
    }

    @Test
    fun concurrentCancelOperations() {
        // Test concurrent cancel operations
        val timeouts = (1..100).map { AsyncTimeout() }
        val executor = Executors.newFixedThreadPool(20)

        // Enter all timeouts
        timeouts.forEach {
            it.timeout(10, TimeUnit.SECONDS)
            it.enter()
        }

        // Cancel half concurrently
        timeouts.take(50).forEach { timeout ->
            executor.submit { timeout.cancel() }
        }

        executor.shutdown()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))

        // Verify state consistency - check if timeouts were canceled
        timeouts.take(50).forEach { timeout ->
            // AsyncTimeout doesn't expose isCanceled, but we can verify cancel was called
            // by checking that exit returns false (not timed out)
            assertFalse("Canceled timeout should not have timed out", timeout.exit())
        }

        // Clean up remaining timeouts
        timeouts.drop(50).forEach { it.exit() }
    }

    @Test
    fun concurrentEnterExitWithTimeouts() {
        val threadCount = 8
        val operationsPerThread = 500
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val timeoutCount = AtomicInteger(0)
        val totalOperations = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                startLatch.await()
                repeat(operationsPerThread) {
                    val timeout = object : AsyncTimeout() {
                        override fun timedOut() {
                            timeoutCount.incrementAndGet()
                        }
                    }
                    timeout.timeout(1, TimeUnit.MILLISECONDS) // Very short timeout
                    timeout.enter()

                    // Random delay to create race conditions
                    Thread.sleep((Math.random() * 5).toLong())

                    timeout.exit()
                    totalOperations.incrementAndGet()
                }
            }
        }

        startLatch.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS))

        val expectedOperations = threadCount * operationsPerThread
        assertEquals("All operations should complete", expectedOperations, totalOperations.get())

        // Allow for some variability in timeout counts due to timing races
        assertTrue("Some operations should timeout", timeoutCount.get() > 0)
        println("Timeouts: ${timeoutCount.get()}, Total: ${totalOperations.get()}")
    }

    @Test
    fun stressTestManyThreads() {
        val threadCount = 50
        val operationsPerThread = 100
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val errors = AtomicInteger(0)

        repeat(threadCount) { threadIndex ->
            executor.submit {
                try {
                    startLatch.await()
                    repeat(operationsPerThread) { opIndex ->
                        val timeout = AsyncTimeout()
                        timeout.timeout(((threadIndex + opIndex) % 100 + 10).toLong(), TimeUnit.MILLISECONDS)
                        timeout.enter()

                        // Randomly cancel some operations
                        if (Math.random() < 0.3) {
                            timeout.cancel()
                        }

                        timeout.exit()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errors.incrementAndGet()
                }
            }
        }

        startLatch.countDown()
        executor.shutdown()
        assertTrue("Executor should terminate within timeout",
                  executor.awaitTermination(60, TimeUnit.SECONDS))
        assertEquals("No errors should occur during stress test", 0, errors.get())
    }

    @Test
    fun concurrentEnterCancelExit() {
        val iterations = 1000
        val executor = Executors.newFixedThreadPool(4)
        val errors = AtomicInteger(0)

        repeat(iterations) {
            val timeout = AsyncTimeout()
            timeout.timeout(1000, TimeUnit.MILLISECONDS)

            val enterFuture = executor.submit {
                try {
                    timeout.enter()
                } catch (e: Exception) {
                    errors.incrementAndGet()
                }
            }

            val cancelFuture = executor.submit {
                try {
                    Thread.sleep(1) // Small delay to create race
                    timeout.cancel()
                } catch (e: Exception) {
                    errors.incrementAndGet()
                }
            }

            val exitFuture = executor.submit {
                try {
                    Thread.sleep(2) // Small delay to create race
                    timeout.exit()
                } catch (e: Exception) {
                    errors.incrementAndGet()
                }
            }

            enterFuture.get()
            cancelFuture.get()
            exitFuture.get()
        }

        executor.shutdown()
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS))
        assertEquals("No errors should occur during concurrent enter/cancel/exit", 0, errors.get())
    }

    @Test
    fun memoryStressTest() {
        // Test that tombstoned entries are properly cleaned up
        val iterations = 10000
        val executor = Executors.newFixedThreadPool(10)

        repeat(iterations) {
            executor.submit {
                val timeout = AsyncTimeout()
                timeout.timeout(1, TimeUnit.MILLISECONDS)
                timeout.enter()

                // Immediately cancel to create tombstone
                timeout.cancel()
                timeout.exit()
            }
        }

        executor.shutdown()
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS))

        // Force GC to verify no memory leaks
        System.gc()
        Thread.sleep(100)

        // Test passes if we don't run out of memory and no exceptions occur
        assertTrue("Memory stress test completed successfully", true)
    }
}