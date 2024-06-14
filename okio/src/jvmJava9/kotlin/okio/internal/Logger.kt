package okio.internal

internal class Logger internal constructor(name: String) {
  private val logger: System.Logger = System.getLogger(name)
  fun warn(msg: String, e: Throwable): Unit = logger.log(System.Logger.Level.WARNING, msg, e)
}
