package okio;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import static okio.Util.UTF_8;

final class BomAwareReader extends Reader {
  private static final char BOM_CHARACTER = '\uFEFF';

  final Charset charset;
  final Object toStringPrefix;
  final InputStreamReader delegate;
  boolean skipBom;

  BomAwareReader(InputStream inputStream, Charset charset, Object toStringPrefix) {
    this.charset = charset;
    this.toStringPrefix = toStringPrefix;

    Charset charsetForReader = charset != null ? charset : UTF_8;
    delegate = new InputStreamReader(inputStream, charsetForReader);
    skipBom = charsetForReader.equals(UTF_8);
  }

  @Override public int read(char[] data, int offset, int charSize) throws IOException {
    if (skipBom) {
      skipBom = false;
      return readAndSkipBom(data, offset, charSize);
    }

    return delegate.read(data, offset, charSize);
  }

  private int readAndSkipBom(char[] data, int offset, int charSize) throws IOException {
    if (charSize == 0) {
      return 0;
    }

    int read = delegate.read();
    if (read == -1) {
      return -1;
    }

    char firstChar = (char) read;
    if (firstChar == BOM_CHARACTER) {
      return delegate.read(data, offset, charSize);
    }

    data[offset] = firstChar;
    read = delegate.read(data, offset + 1, charSize - 1);
    return read == -1 ? 1 : 1 + read;
  }

  @Override public void close() throws IOException {
    delegate.close();
  }

  @Override public String toString() {
    return toStringPrefix + (charset == null
        ? ".readerUtf8()"
        : ".reader(" + charset + ")");
  }
}
