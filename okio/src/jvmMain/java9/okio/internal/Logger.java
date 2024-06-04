package okio.internal;

public class Logger {
  private final System.Logger logger;
  private Logger(String name) {
    this.logger = System.getLogger(name);
  }
  public static Logger getLogger(String name) {
    return new Logger(name);
  }
  public void warn(String msg, Throwable e) {
    logger.log(System.Logger.Level.WARNING, msg, e);
  }
}
