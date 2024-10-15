package okio

import kotlinx.cinterop.UnsafeNumber
import platform.Foundation.NSError
import platform.Foundation.NSLocalizedDescriptionKey
import platform.Foundation.NSUnderlyingErrorKey

@OptIn(UnsafeNumber::class)
internal fun Exception.toNSError() = NSError(
  domain = "Kotlin",
  code = 0,
  userInfo = mapOf(
    NSLocalizedDescriptionKey to message,
    NSUnderlyingErrorKey to this,
  ),
)
