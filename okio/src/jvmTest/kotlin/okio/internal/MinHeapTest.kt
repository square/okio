package okio.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.util.concurrent.TimeUnit
import okio.AsyncTimeout
import okio.Heap
import okio.compareTo
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

  @Test
  fun lastElementIsGreaterThanRemovedElement() {
    val before = """
      |...............5................
      |......10..............20........
      |..11......12......21......22....
      |""".toHeap()

    before.removeFromQueue(before[10])

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............5................
      |......11..............20........
      |..22......12......21............
      |""".trimMargin()
    )
  }

  @Test
  fun lastElementIsLessThanRemovedElement() {
    val before = """
      |...............5................
      |......20..............10........
      |..21......22......11......12....
      |""".toHeap()

    before.removeFromQueue(before[20])

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............5................
      |......12..............10........
      |..21......22......11............
      |""".trimMargin()
    )
  }

  @Test
  fun lastElementIsLessThanRemovedElementParent() {
    val before = """
      |...............................5................................
      |..............10..............................20................
      |......11..............12..............21..............22........
      |..13......14....................................................
      |""".toHeap()

    before.removeFromQueue(before[21])

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............................5................................
      |..............10..............................14................
      |......11..............12..............20..............22........
      |..13............................................................
      |""".trimMargin()
    )
  }


  private fun String.toHeap(): Heap {
    val nodeValues = trimMargin()
      .replace(".", " ")
      .trim()
      .split(Regex("\\s+", RegexOption.MULTILINE))
      .map { it.toLong() }

    val result = Heap()

    val now = System.nanoTime()
    for (i in nodeValues) {
      val node = AsyncTimeout()
      node.timeout(i, TIME_UNIT)
      result.insertIntoQueue(now, node)
    }

    val formattedBack = result.toDebugString()
    assertThat(formattedBack, "input is well-formatted").isEqualTo(this.trimMargin())

    return result
  }

  private operator fun Heap.get(value: Long): AsyncTimeout {
    return array.single { it?.timeoutNanos() == TIME_UNIT.toNanos(value) }!!
  }

  /**
   * Returns a string like this:
   *
   * ```
   * ...............5................
   * ......12..............10........
   * ..21......22......11............
   * ```
   */
  private fun Heap.toDebugString() = buildString {
    val nodeWidth = 4
    val height = Integer.numberOfTrailingZeros(Integer.highestOneBit(heapSize)) + 1
    val width = (1 shl height) - 1
    var index = 1 // Heap's first element is at 1!
    for (row in 0 until height) {
      val printAt = height - row - 1
      for (column in 1..width) {
        if (Integer.numberOfTrailingZeros(column) != printAt || index >= array.size || array[index] == null) {
          append(".".repeat(nodeWidth))
        } else {
          val nodeValue = TIME_UNIT.convert(array[index++]!!.timeoutNanos(), TimeUnit.NANOSECONDS).toString()
          append(".".repeat(nodeWidth - nodeValue.length))
          append(nodeValue)
        }
      }
      append(".".repeat(nodeWidth))
      append("\n")
    }
  }

  companion object {
    val TIME_UNIT = TimeUnit.SECONDS
    val NUMBER_OF_NODES = 1000
  }
}

internal fun validateHeap(heap: Heap) {
  val array = heap.array
  val arrayHead = array[1]
  if (arrayHead == null) {
    assertThat(heap.heapSize == 0) { "Heap root is null but heapSize > 0" }
    return
  }

  val queue = ArrayDeque<AsyncTimeout>()
  queue.add(arrayHead)

  var index = 1
  while (queue.isNotEmpty()) {
    val current = queue.removeFirst()

    // Check left child
    val leftIndex = current.index shl 1
    if (leftIndex > heap.heapSize) break
    val left = array[leftIndex]
    left?.let {
      assertThat(it >= current) {
        "Heap property violated at node $current: left child $it is smaller."
      }
      queue.add(it)
    }

    // Check right child
    val rightIndex = leftIndex + 1
    val right = array[rightIndex]
    if (rightIndex > heap.heapSize) break
    right?.let {
      assertThat(it >= current) {
        "Heap property violated at node $current: right child $it is smaller."
      }
      queue.add(it)
    }

    // Ensure the heap is a complete binary tree
    assertThat(left != null || right == null) {
      "Heap structure violated: node $current has a right child but no left child."
    }

    index++
  }

  assertThat(index == heap.heapSize) {
    "Heap size mismatch: expected $index, but got ${heap.heapSize}."
  }
}
