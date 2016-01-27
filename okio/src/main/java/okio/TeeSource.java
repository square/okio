package okio;

import java.io.IOException;


class TeeSource implements Source {

  private final Source source;
  private final BufferedSink cacheBody;

  public TeeSource(Source source, BufferedSink cacheBody) {
    this.source = source;
    this.cacheBody = cacheBody;
  }

  @Override
  public long read(Buffer sink, long byteCount) throws IOException {
    long bytesRead;
    bytesRead = source.read(sink, byteCount);

    if (bytesRead == -1) {
      cacheBody.close(); // The cache response is complete!
      return -1;
    }

    sink.copyTo(cacheBody.buffer(), sink.size() - bytesRead, bytesRead);
    cacheBody.emitCompleteSegments();
    return bytesRead;
  }

  @Override
  public Timeout timeout() {
    return source.timeout();
  }

  @Override
  public void close() throws IOException {
    cacheBody.close();
    source.close();
  }
}
