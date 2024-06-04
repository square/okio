package okio.internal;

public class Logger {
  private final java.util.logging.Logger logger;
  private Logger(String name) {
    this.logger = java.util.logging.Logger.getLogger(name);
  }
  public static Logger getLogger(String name) {
    return new Logger(name);
  }
  public void warn(String msg, Throwable e) {
    logger.log(java.util.logging.Level.WARNING, msg, e);
  }
}
