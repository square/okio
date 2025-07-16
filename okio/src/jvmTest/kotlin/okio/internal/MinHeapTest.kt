package okio.internal

import assertk.assertThat
import java.util.concurrent.TimeUnit
import okio.AsyncTimeout
import okio.compareTo
import okio.Heap
import org.junit.Before
import org.junit.Test

// https://publicobject.com/2017/02/06/story-code/
// How to test this?

class MinHeapTest {

  private val nodes = ArrayDeque<AsyncTimeout>()
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

    heap = Heap()
    heap.head = AsyncTimeout()

    val now = System.nanoTime()
    nodes.forEachIndexed { i, node ->
      heap.insertIntoQueue(now, node)
      validateHeap(heap)
    }
  }

  @Test
  fun heapIsConsistentAfterRemoveFirst() {
    while (nodes.isNotEmpty()) {
      val node = nodes.removeFirst()
      heap.removeFirst(node)
      validateHeap(heap)
    }
  }

  @Test
  fun minHeapConsistentAfterRandomRemovals() {
    nodes.shuffle()
    while (nodes.isNotEmpty()) {
      val node = nodes.removeFirst()
      heap.removeFromQueue(node)
      validateHeap(heap)
    }
  }

  @Test
  fun firstReturnsSmallestElement() {
    while (nodes.isNotEmpty()) {
      val node = nodes.removeFirst()
      assertThat(node === heap.first())
      heap.removeFirst(node)
      validateHeap(heap)
    }
  }

  companion object {
    val TIME_UNIT = TimeUnit.SECONDS
    val NUMBER_OF_NODES = 1000
  }
}

internal fun validateHeap(heap: Heap) {
  val head = heap.head
  val heapSize = heap.heapSize
  if (head?.left == null) {
    assertThat(heapSize == 0) { "Heap root is null but heapSize > 0" }
    return
  }

  val queue = ArrayDeque<AsyncTimeout>()
  queue.add(head.left!!)

  var index = 1
  while (queue.isNotEmpty()) {
    val current = queue.removeFirst()

    // Check left child
    current.left?.let {
      assertThat(it >= current) {
        "Heap property violated at node $current: left child $it is smaller."
      }
      queue.add(it)
    }

    // Check right child
    current.right?.let {
      assertThat(it >= current) {
        "Heap property violated at node $current: right child $it is smaller."
      }
      queue.add(it)
    }

    // Ensure the heap is a complete binary tree
    assertThat(current.left != null || current.right == null) {
      "Heap structure violated: node $current has a right child but no left child."
    }

    index++
  }

  assertThat(index - 1 == heapSize) {
    "Heap size mismatch: expected $index, but got $heapSize."
  }
}
