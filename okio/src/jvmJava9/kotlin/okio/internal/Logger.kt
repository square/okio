package okio.internal

class Logger(name: String) {
  private val logger: System.Logger = System.getLogger(name)
  fun warn(msg: String, e: Throwable): Unit = logger.log(System.Logger.Level.WARNING, msg, e)
}
