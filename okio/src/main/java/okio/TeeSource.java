package okio;

import java.io.IOException;


class TeeSource implements Source {

  private final Source source;
  private final BufferedSink sink;

  public TeeSource(Source source, BufferedSink copySink) {
    this.source = source;
    this.sink = copySink;
  }

  @Override
  public long read(Buffer sink, long byteCount) throws IOException {
    long bytesRead;
    bytesRead = source.read(sink, byteCount);

    if (bytesRead == -1) {
      this.sink.close(); // The cache response is complete!
      return -1;
    }

    sink.copyTo(this.sink.buffer(), sink.size() - bytesRead, bytesRead);
    this.sink.emitCompleteSegments();
    return bytesRead;
  }

  @Override
  public Timeout timeout() {
    return source.timeout();
  }

  @Override
  public void close() throws IOException {
    sink.close();
    source.close();
  }
}
