package okio.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.util.concurrent.TimeUnit
import okio.AsyncTimeout
import okio.Heap
import org.junit.Test

class MinHeapTest {
  val TIME_UNIT = TimeUnit.SECONDS

  @Test
  fun insertedElementIsSmallerThanItsAncestors() {
    val now = System.nanoTime()
    val before = """
      |...............5................
      |......10..............20........
      |..28......29......21............
      |""".toHeap()

    before.insertIntoQueue(newAsyncTimeout(2))

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............2................
      |......10...............5........
      |..28......29......21......20....
      |""".trimMargin()
    )
  }

  @Test
  fun insertedElementIsSmallerThanParent() {
    val now = System.nanoTime()
    val before = """
      |...............5................
      |......10..............20........
      |..28......29......21............
      |""".toHeap()

    before.insertIntoQueue(newAsyncTimeout(15))

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............5................
      |......10..............15........
      |..28......29......21......20....
      |""".trimMargin()
    )
  }

  @Test
  fun lastElementIsGreaterThanOneOfRemovedElementChildren() {
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
  fun insertedElementIsLargerThanBothItsParent() {
    val now = System.nanoTime()
    val before = """
      |...............5................
      |......10..............20........
      |..28......29......21............
      |""".toHeap()

    before.insertIntoQueue(newAsyncTimeout(24))

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............5................
      |......10..............20........
      |..28......29......21......24....
      |""".trimMargin()
    )
  }

  @Test
  fun lastElementIsGreaterThanRemovedElement() {
    val before = """
      |...............5................
      |......10..............20........
      |..28......29......21......22....
      |""".toHeap()

    before.removeFromQueue(before[10])

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............5................
      |......22..............20........
      |..28......29......21............
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

  private fun newAsyncTimeout(value: Long) = AsyncTimeout().let {
    it.timeout(value, TIME_UNIT)
    return@let it
  }

  private fun String.toHeap(): Heap {
    val nodeValues = trimMargin()
      .replace(".", " ")
      .trim()
      .split(Regex("\\s+", RegexOption.MULTILINE))
      .map { it.toLong() }

    val result = Heap()

    for (i in nodeValues) {
      result.insertIntoQueue(newAsyncTimeout(i))
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
}
