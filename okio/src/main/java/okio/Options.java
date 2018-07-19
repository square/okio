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
package okio;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;

/** An indexed set of values that may be read with {@link BufferedSource#select}. */
public final class Options extends AbstractList<ByteString> implements RandomAccess {
  final ByteString[] byteStrings;
  final int[] trie;

  private Options(ByteString[] byteStrings, int[] trie) {
    this.byteStrings = byteStrings;
    this.trie = trie;
  }

  public static Options of(ByteString... byteStrings) {
    if (byteStrings.length == 0) throw new IllegalArgumentException("no options provided");

    // Sort the byte strings which is required when recursively building the trie. Map the sorted
    // indexes to the caller's indexes.
    List<ByteString> list = new ArrayList<>(Arrays.asList(byteStrings));
    Collections.sort(list);
    List<Integer> indexes = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      indexes.add(-1);
    }
    for (int i = 0; i < list.size(); i++) {
      int sortedIndex = Collections.binarySearch(list, byteStrings[i]);
      indexes.set(sortedIndex, i);
    }
    if (list.get(0).size() == 0) {
      throw new IllegalArgumentException("the empty byte string is not a supported option");
    }

    // Strip elements that will never be returned because they follow their own prefixes. For
    // example, if the caller provides ["abc", "abcde"] we will never return "abcde" because we
    // return as soon as we encounter "abc".
    for (int a = 0; a < list.size(); a++) {
      ByteString prefix = list.get(a);
      for (int b = a + 1; b < list.size(); ) {
        ByteString byteString = list.get(b);
        if (!byteString.startsWith(prefix)) break;
        if (byteString.size() == prefix.size()) {
          throw new IllegalArgumentException("duplicate option: " + byteString);
        }
        if (indexes.get(b) > indexes.get(a)) {
          list.remove(b);
          indexes.remove(b);
        } else {
          b++;
        }
      }
    }

    Buffer trieBytes = new Buffer();
    buildTrieRecursive(0L, trieBytes, 0, list, 0, list.size(), indexes);

    int[] trie = new int[intCount(trieBytes)];
    for (int i = 0; i < trie.length; i++) {
      trie[i] = trieBytes.readInt();
    }
    if (!trieBytes.exhausted()) {
      throw new AssertionError();
    }

    return new Options(byteStrings.clone() /* Defensive copy. */, trie);
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
  private static void buildTrieRecursive(
      long nodeOffset,
      Buffer node,
      int byteStringOffset,
      List<ByteString> byteStrings,
      int fromIndex,
      int toIndex,
      List<Integer> indexes) {
    if (fromIndex >= toIndex) throw new AssertionError();
    for (int i = fromIndex; i < toIndex; i++) {
      if (byteStrings.get(i).size() < byteStringOffset) throw new AssertionError();
    }

    ByteString from = byteStrings.get(fromIndex);
    ByteString to = byteStrings.get(toIndex - 1);
    int prefixIndex = -1;

    // If the first element is already matched, that's our prefix.
    if (byteStringOffset == from.size()) {
      prefixIndex = indexes.get(fromIndex);
      fromIndex++;
      from = byteStrings.get(fromIndex);
    }

    if (from.getByte(byteStringOffset) != to.getByte(byteStringOffset)) {
      // If we have multiple bytes to choose from, encode a SELECT node.
      int selectChoiceCount = 1;
      for (int i = fromIndex + 1; i < toIndex; i++) {
        if (byteStrings.get(i - 1).getByte(byteStringOffset)
            != byteStrings.get(i).getByte(byteStringOffset)) {
          selectChoiceCount++;
        }
      }

      // Compute the offset that childNodes will get when we append it to node.
      long childNodesOffset = nodeOffset + intCount(node) + 2 + (selectChoiceCount * 2);

      node.writeInt(selectChoiceCount);
      node.writeInt(prefixIndex);

      for (int i = fromIndex; i < toIndex; i++) {
        byte rangeByte = byteStrings.get(i).getByte(byteStringOffset);
        if (i == fromIndex || rangeByte != byteStrings.get(i - 1).getByte(byteStringOffset)) {
          node.writeInt(rangeByte & 0xff);
        }
      }

      Buffer childNodes = new Buffer();
      int rangeStart = fromIndex;
      while (rangeStart < toIndex) {
        byte rangeByte = byteStrings.get(rangeStart).getByte(byteStringOffset);
        int rangeEnd = toIndex;
        for (int i = rangeStart + 1; i < toIndex; i++) {
          if (rangeByte != byteStrings.get(i).getByte(byteStringOffset)) {
            rangeEnd = i;
            break;
          }
        }

        if (rangeStart + 1 == rangeEnd
            && byteStringOffset + 1 == byteStrings.get(rangeStart).size()) {
          // The result is a single index.
          node.writeInt(indexes.get(rangeStart));
        } else {
          // The result is another node.
          node.writeInt((int) (-1 * (childNodesOffset + intCount(childNodes))));
          buildTrieRecursive(
              childNodesOffset,
              childNodes,
              byteStringOffset + 1,
              byteStrings,
              rangeStart,
              rangeEnd,
              indexes);
        }

        rangeStart = rangeEnd;
      }

      node.write(childNodes, childNodes.size());

    } else {
      // If all of the bytes are the same, encode a SCAN node.
      int scanByteCount = 0;
      for (int i = byteStringOffset, max = Math.min(from.size(), to.size()); i < max; i++) {
        if (from.getByte(i) == to.getByte(i)) {
          scanByteCount++;
        } else {
          break;
        }
      }

      // Compute the offset that childNodes will get when we append it to node.
      long childNodesOffset = nodeOffset + intCount(node) + 2 + scanByteCount + 1;

      node.writeInt(-scanByteCount);
      node.writeInt(prefixIndex);

      for (int i = byteStringOffset; i < byteStringOffset + scanByteCount; i++) {
        node.writeInt(from.getByte(i) & 0xff);
      }

      if (fromIndex + 1 == toIndex) {
        // The result is a single index.
        if (byteStringOffset + scanByteCount != byteStrings.get(fromIndex).size()) {
          throw new AssertionError();
        }
        node.writeInt(indexes.get(fromIndex));
      } else {
        // The result is another node.
        Buffer childNodes = new Buffer();
        node.writeInt((int) (-1 * (childNodesOffset + intCount(childNodes))));
        buildTrieRecursive(
            childNodesOffset,
            childNodes,
            byteStringOffset + scanByteCount,
            byteStrings,
            fromIndex,
            toIndex,
            indexes);
        node.write(childNodes, childNodes.size());
      }
    }
  }

  @Override public ByteString get(int i) {
    return byteStrings[i];
  }

  @Override public final int size() {
    return byteStrings.length;
  }

  private static int intCount(Buffer trieBytes) {
    return (int) (trieBytes.size() / 4);
  }
}
