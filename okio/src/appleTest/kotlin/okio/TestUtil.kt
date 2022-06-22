@file:OptIn(UnsafeNumber::class)

package okio

import kotlin.test.assertTrue
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import platform.Foundation.NSData
import platform.Foundation.create


fun ByteArray.toNSData(): NSData = memScoped {
  NSData.create(bytes = allocArrayOf(this@toNSData), length = size.convert())
}

fun assertNoEmptySegments(buffer: Buffer) {
  assertTrue(segmentSizes(buffer).all { it != 0 }, "Expected all segments to be non-empty")
}
