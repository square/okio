package okio.internal

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import okio.AsyncTimeout
import okio.Heap
import okio.Heap.Companion.compareTo
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MinHeapTest {

  private val nodes = mutableListOf<AsyncTimeout>()
  private lateinit var heap: Heap
  @Before
  fun before() {
    // Initialize the heap with 100 random values.
    (1L..NUMBER_OF_NODES).forEach { i ->
      AsyncTimeout().let { node ->
        node.timeout(3600 + i, TIME_UNIT)
        nodes.add(node)
      }
    }

    heap = object : Heap() {
      override fun signal() { /* No-op */ }
    }
    heap.head = AsyncTimeout()
  }

  @Test
  fun heapTreeIsComplete() {

  }

  @Test
  fun AllChildrenNodesAreSmallerThanParent() {
    // Test that the minimum heap property holds for all nodes.
    // Each child node should be smaller than its parent node.
    val now = System.nanoTime()
    nodes.forEachIndexed { i, node ->
      heap.insertIntoQueue(now, node)
      assertTrue(checkConsistency(heap.head!!))
      assertEquals(i+1, heap.heapSize)
    }
  }

  @Test
  fun minHeapConsistentAfterRandomRemovals() {
    // Insert random values into the heap and check consistency.
    val now = System.nanoTime()
    nodes.forEachIndexed { i, node ->
      heap.insertIntoQueue(now, node)
      assertTrue(checkConsistency(heap.head!!))
      assertEquals(i+1, heap.heapSize)
    }

    nodes.shuffle()

    (1 .. nodes.size).forEach { i ->
      // println("Removing node $i: ${nodes[i-1]}")
      val node = nodes[i-1]
      heap.removeFromQueue(node)
      assertTrue(checkConsistency(heap.head!!))
      assertEquals(nodes.size - i, heap.heapSize)
    }
  }

  @Test
  fun minHeapRemovesFirstSmallestValue() {
    // Insert random values into the heap and check consistency.
    val now = System.nanoTime()
    nodes.forEachIndexed { i, node ->
      heap.insertIntoQueue(now, node)
      assertTrue(checkConsistency(heap.head!!))
      assertEquals(i+1, heap.heapSize)
    }

    val removed = mutableListOf<AsyncTimeout>()
    (1 .. nodes.size).forEach { i ->
      // println("Removing node $i: ${nodes[i-1].timeoutNanos()}")
      val node = nodes[i-1]
      val root = heap.first()!!


      assertEquals(toNanos(3600 + i), root.timeoutNanos())
      heap.removeFirst(root)
      assertTrue(checkConsistency(heap.head!!))
      assertEquals(nodes.size - i, heap.heapSize)
      removed.add(root)
    }

    assertThat(removed).containsExactlyInAnyOrder(*nodes.toTypedArray())
  }

  @Test
  fun canRetreiveLastNode() {

  }



  @After
  fun after() {

  }

  companion object {
    val TIME_UNIT = TimeUnit.SECONDS
    val NUMBER_OF_NODES = 1000

    fun toNanos(value: Int) = TIME_UNIT.toNanos(value.toLong())
  }
}

// This function should verify that the heap structure is maintained
// and that all nodes follow the min-heap property.
internal fun checkConsistency(rootNode: AsyncTimeout, now: Long = System.nanoTime()): Boolean {
  // Check the left child node
  rootNode.left?.let {
    if (compareTo(it, rootNode, now) < 0) return false
    if (!checkConsistency(it, now)) return false
  }

  // Check the right child node
  rootNode.right?.let {
    if (compareTo(it, rootNode, now) < 0) return false
    if (!checkConsistency(it, now)) return false
  }

  return true
}
