package com.squareup.okio.benchmarks

import okio.internal.commonAsUtf8ToByteArray
import okio.internal.commonToUtf8String

// Necessary to make an invisible functions visible to Java.
object BenchmarkUtils {
  @JvmStatic
  fun ByteArray.decodeUtf8(): String {
    return commonToUtf8String()
  }

  @JvmStatic
  fun String.encodeUtf8(): ByteArray {
    return commonAsUtf8ToByteArray()
  }
}
