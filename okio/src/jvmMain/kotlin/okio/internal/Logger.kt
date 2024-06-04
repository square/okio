package okio.internal

internal class Logger internal constructor(name: String) {
  private val logger: java.util.logging.Logger = java.util.logging.Logger.getLogger(name)
  fun warn(msg: String, e: Throwable): Unit = logger.log(java.util.logging.Level.WARNING, msg, e)
}
