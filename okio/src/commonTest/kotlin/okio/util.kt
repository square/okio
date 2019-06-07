package okio

import kotlin.random.Random

fun Char.repeat(count: Int): String {
  return toString().repeat(count)
}

fun segmentSizes(buffer: Buffer): List<Int> {
  return generateSequence(buffer.head) { segment -> segment.next.takeIf { it !== buffer.head }}
    .map { segment -> segment.limit - segment.pos }
    .toList()
}

fun bufferWithRandomSegmentLayout(dice: Random, data: ByteArray): Buffer {
  val result = Buffer()

  // Writing to result directly will yield packed segments. Instead, write to
  // other buffers, then write those buffers to result.
  var pos = 0
  var byteCount: Int
  while (pos < data.size) {
    byteCount = Segment.SIZE / 2 + dice.nextInt(Segment.SIZE / 2)
    if (byteCount > data.size - pos) byteCount = data.size - pos
    val offset = dice.nextInt(Segment.SIZE - byteCount)

    val segment = Buffer()
    segment.write(ByteArray(offset))
    segment.write(data, pos, byteCount)
    segment.skip(offset.toLong())

    result.write(segment, byteCount.toLong())
    pos += byteCount
  }

  return result
}

fun bufferWithSegments(vararg segments: String): Buffer {
  val result = Buffer()
  for (s in segments) {
    val offsetInSegment = if (s.length < Segment.SIZE) (Segment.SIZE - s.length) / 2 else 0
    val buffer = Buffer()
    buffer.writeUtf8('_'.repeat(offsetInSegment))
    buffer.writeUtf8(s)
    buffer.skip(offsetInSegment.toLong())
    result.write(buffer.copyTo(Buffer()), buffer.size)
  }
  return result
}
