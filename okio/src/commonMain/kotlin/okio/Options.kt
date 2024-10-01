/*
 * Copyright (C) 2016 Square, Inc.
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

import kotlin.jvm.JvmStatic

/**
 * An indexed set of values that may be read with [BufferedSource.select].
 *
 * Also consider [TypedOptions] to select a typed value _T_.
 */
class Options private constructor(
  internal val byteStrings: Array<out ByteString>,
  internal val trie: IntArray,
) : AbstractList<ByteString>(), RandomAccess {

  override val size: Int
    get() = byteStrings.size

  override fun get(index: Int) = byteStrings[index]

  companion object {
    @JvmStatic
    fun of(vararg byteStrings: ByteString): Options {
      if (byteStrings.isEmpty()) {
        // With no choices we must always return -1. Create a trie that selects from an empty set.
        return Options(arrayOf(), intArrayOf(0, -1))
      }

      // Sort the byte strings which is required when recursively building the trie. Map the sorted
      // indexes to the caller's indexes.
      val list = byteStrings.toMutableList()
      list.sort()
      val indexes = MutableList(list.size) { -1 }
      byteStrings.forEachIndexed { callerIndex, byteString ->
        val sortedIndex = list.binarySearch(byteString)
        indexes[sortedIndex] = callerIndex
      }
      require(list[0].size > 0) { "the empty byte string is not a supported option" }

      // Strip elements that will never be returned because they follow their own prefixes. For
      // example, if the caller provides ["abc", "abcde"] we will never return "abcde" because we
      // return as soon as we encounter "abc".
      var a = 0
      while (a < list.size) {
        val prefix = list[a]
        var b = a + 1
        while (b < list.size) {
          val byteString = list[b]
          if (!byteString.startsWith(prefix)) break
          require(byteString.size != prefix.size) { "duplicate option: $byteString" }
          if (indexes[b] > indexes[a]) {
            list.removeAt(b)
            indexes.removeAt(b)
          } else {
            b++
          }
        }
        a++
      }

      val trieBytes = Buffer()
      buildTrieRecursive(node = trieBytes, byteStrings = list, indexes = indexes)

      val trie = IntArray(trieBytes.intCount.toInt()) {
        trieBytes.readInt()
      }

      return Options(byteStrings.copyOf() /* Defensive copy. */, trie)
    }

    /**
     * Builds a trie encoded as an int array. Nodes in the trie are of two types: SELECT and SCAN.
     *
     * SELECT nodes are encoded as:
     *  - selectChoiceCount: the number of bytes to choose between (a positive int)
     *  - prefixIndex: the result index at the current position or -1 if the current position is not
     *    a result on its own
     *  - a sorted list of selectChoiceCount bytes to match against the input string
     *  - a heterogeneous list of selectChoiceCount result indexes (>= 0) or offsets (< 0) of the
     *    next node to follow. Elements in this list correspond to elements in the preceding list.
     *    Offsets are negative and must be multiplied by -1 before being used.
     *
     * SCAN nodes are encoded as:
     *  - scanByteCount: the number of bytes to match in sequence. This count is negative and must
     *    be multiplied by -1 before being used.
     *  - prefixIndex: the result index at the current position or -1 if the current position is not
     *    a result on its own
     *  - a list of scanByteCount bytes to match
     *  - nextStep: the result index (>= 0) or offset (< 0) of the next node to follow. Offsets are
     *    negative and must be multiplied by -1 before being used.
     *
     * This structure is used to improve locality and performance when selecting from a list of
     * options.
     */
    private fun buildTrieRecursive(
      nodeOffset: Long = 0L,
      node: Buffer,
      byteStringOffset: Int = 0,
      byteStrings: List<ByteString>,
      fromIndex: Int = 0,
      toIndex: Int = byteStrings.size,
      indexes: List<Int>,
    ) {
      require(fromIndex < toIndex)
      for (i in fromIndex until toIndex) {
        require(byteStrings[i].size >= byteStringOffset)
      }

      var fromIndex = fromIndex
      var from = byteStrings[fromIndex]
      val to = byteStrings[toIndex - 1]
      var prefixIndex = -1

      // If the first element is already matched, that's our prefix.
      if (byteStringOffset == from.size) {
        prefixIndex = indexes[fromIndex]
        fromIndex++
        from = byteStrings[fromIndex]
      }

      if (from[byteStringOffset] != to[byteStringOffset]) {
        // If we have multiple bytes to choose from, encode a SELECT node.
        var selectChoiceCount = 1
        for (i in fromIndex + 1 until toIndex) {
          if (byteStrings[i - 1][byteStringOffset] != byteStrings[i][byteStringOffset]) {
            selectChoiceCount++
          }
        }

        // Compute the offset that childNodes will get when we append it to node.
        val childNodesOffset = nodeOffset + node.intCount + 2 + (selectChoiceCount * 2)

        node.writeInt(selectChoiceCount)
        node.writeInt(prefixIndex)

        for (i in fromIndex until toIndex) {
          val rangeByte = byteStrings[i][byteStringOffset]
          if (i == fromIndex || rangeByte != byteStrings[i - 1][byteStringOffset]) {
            node.writeInt(rangeByte and 0xff)
          }
        }

        val childNodes = Buffer()
        var rangeStart = fromIndex
        while (rangeStart < toIndex) {
          val rangeByte = byteStrings[rangeStart][byteStringOffset]
          var rangeEnd = toIndex
          for (i in rangeStart + 1 until toIndex) {
            if (rangeByte != byteStrings[i][byteStringOffset]) {
              rangeEnd = i
              break
            }
          }

          if (rangeStart + 1 == rangeEnd &&
            byteStringOffset + 1 == byteStrings[rangeStart].size
          ) {
            // The result is a single index.
            node.writeInt(indexes[rangeStart])
          } else {
            // The result is another node.
            node.writeInt(-1 * (childNodesOffset + childNodes.intCount).toInt())
            buildTrieRecursive(
              nodeOffset = childNodesOffset,
              node = childNodes,
              byteStringOffset = byteStringOffset + 1,
              byteStrings = byteStrings,
              fromIndex = rangeStart,
              toIndex = rangeEnd,
              indexes = indexes,
            )
          }

          rangeStart = rangeEnd
        }

        node.writeAll(childNodes)
      } else {
        // If all of the bytes are the same, encode a SCAN node.
        var scanByteCount = 0
        for (i in byteStringOffset until minOf(from.size, to.size)) {
          if (from[i] == to[i]) {
            scanByteCount++
          } else {
            break
          }
        }

        // Compute the offset that childNodes will get when we append it to node.
        val childNodesOffset = nodeOffset + node.intCount + 2 + scanByteCount + 1

        node.writeInt(-scanByteCount)
        node.writeInt(prefixIndex)

        for (i in byteStringOffset until byteStringOffset + scanByteCount) {
          node.writeInt(from[i] and 0xff)
        }

        if (fromIndex + 1 == toIndex) {
          // The result is a single index.
          check(byteStringOffset + scanByteCount == byteStrings[fromIndex].size)
          node.writeInt(indexes[fromIndex])
        } else {
          // The result is another node.
          val childNodes = Buffer()
          node.writeInt(-1 * (childNodesOffset + childNodes.intCount).toInt())
          buildTrieRecursive(
            nodeOffset = childNodesOffset,
            node = childNodes,
            byteStringOffset = byteStringOffset + scanByteCount,
            byteStrings = byteStrings,
            fromIndex = fromIndex,
            toIndex = toIndex,
            indexes = indexes,
          )
          node.writeAll(childNodes)
        }
      }
    }

    private val Buffer.intCount get() = size / 4
  }
}
