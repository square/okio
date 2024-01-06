package okio

import kotlin.jvm.JvmStatic

interface EnumOption {
  val byteString: ByteString
}

abstract class EnumOptions<E : EnumOption>(val entries: List<E>) {
  val options: Options = Options.of(*entries.map { it.byteString }.toTypedArray())

  companion object {
    inline fun <reified E> of(): EnumOptions<E> where E : EnumOption, E : Enum<E> {
      return object :
        EnumOptions<E>(enumValues<E>().toList()) {} // Change to enumEntries<E>() when stable.
    }

    @JvmStatic
    fun <E : EnumOption> of(entries: List<E>): EnumOptions<E> {
      return object : EnumOptions<E>(entries) {}
    }
  }
}

fun <E : EnumOption> BufferedSource.select(options: EnumOptions<E>): E? {
  val index = select(options.options)
  return if (index == -1) null else options.entries[index]
}
