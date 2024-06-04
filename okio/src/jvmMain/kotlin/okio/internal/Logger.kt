package okio.internal

class Logger(name: String) {
  private val logger: java.util.logging.Logger = java.util.logging.Logger.getLogger(name)
  fun warn(msg: String, e: Throwable): Unit = logger.log(java.util.logging.Level.WARNING, msg, e)
}
