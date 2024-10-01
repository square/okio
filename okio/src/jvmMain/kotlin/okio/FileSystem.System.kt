@file:JvmName("SystemFileSystem")

package okio

/*
 * JVM and native platforms do offer a [SYSTEM] [FileSystem], however we cannot refine an 'expect' companion object.
 * Therefore an extension property is provided, which on respective platforms (here JVM) will be shadowed by the
 * original implementation.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
actual inline val FileSystem.Companion.SYSTEM: FileSystem
  @JvmSynthetic
  get() = SYSTEM
