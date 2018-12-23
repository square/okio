package okio

import java.util.Random

import okio.TestUtil.bufferWithRandomSegmentLayout
import okio.TestUtil.bufferWithSegments

enum class BufferFactory {
  EMPTY {
    override fun newBuffer(): Buffer {
      return Buffer()
    }
  },

  SMALL_BUFFER {
    override fun newBuffer(): Buffer {
      return Buffer().writeUtf8("abcde")
    }
  },

  SMALL_SEGMENTED_BUFFER {
    @Throws(Exception::class)
    override fun newBuffer(): Buffer {
      return bufferWithSegments("abc", "defg", "hijkl")
    }
  },

  LARGE_BUFFER {
    @Throws(Exception::class)
    override fun newBuffer(): Buffer {
      val dice = Random(0)
      val largeByteArray = ByteArray(512 * 1024)
      dice.nextBytes(largeByteArray)

      return Buffer().write(largeByteArray)
    }
  },

  LARGE_BUFFER_WITH_RANDOM_LAYOUT {
    @Throws(Exception::class)
    override fun newBuffer(): Buffer {
      val dice = Random(0)
      val largeByteArray = ByteArray(512 * 1024)
      dice.nextBytes(largeByteArray)

      return bufferWithRandomSegmentLayout(dice, largeByteArray)
    }
  };

  @Throws(Exception::class)
  internal abstract fun newBuffer(): Buffer
}
