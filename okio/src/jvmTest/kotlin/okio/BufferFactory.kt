/*
 * Copyright (C) 2019 Square, Inc.
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

import java.util.Random
import okio.TestUtil.bufferWithRandomSegmentLayout
import okio.TestUtil.bufferWithSegments

enum class BufferFactory {
  Empty {
    override fun newBuffer(): Buffer {
      return Buffer()
    }
  },

  SmallBuffer {
    override fun newBuffer(): Buffer {
      return Buffer().writeUtf8("abcde")
    }
  },

  SmallSegmentedBuffer {
    @Throws(Exception::class)
    override fun newBuffer(): Buffer {
      return bufferWithSegments("abc", "defg", "hijkl")
    }
  },

  LargeBuffer {
    @Throws(Exception::class)
    override fun newBuffer(): Buffer {
      val dice = Random(0)
      val largeByteArray = ByteArray(512 * 1024)
      dice.nextBytes(largeByteArray)

      return Buffer().write(largeByteArray)
    }
  },

  LargeBufferWithRandomLayout {
    @Throws(Exception::class)
    override fun newBuffer(): Buffer {
      val dice = Random(0)
      val largeByteArray = ByteArray(512 * 1024)
      dice.nextBytes(largeByteArray)

      return bufferWithRandomSegmentLayout(dice, largeByteArray)
    }
  },
  ;

  @Throws(Exception::class)
  abstract fun newBuffer(): Buffer
}
