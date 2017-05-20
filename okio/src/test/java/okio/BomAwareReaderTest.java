package okio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import org.junit.Test;

import static okio.TestUtil.readerToString;
import static okio.Util.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BomAwareReaderTest {
  static final ByteString UTF_8_BOM = ByteString.decodeHex("EFBBBF");

  @Test public void readZeroCharacters() throws Exception {
    Buffer buffer = new Buffer();
    buffer.writeUtf8("1");
    Reader reader = newReader(buffer.inputStream(), UTF_8);

    int read = reader.read(new char[0], 0, 0);

    assertEquals(0, read);
  }

  @Test public void readOneCharacter() throws Exception {
    Buffer buffer = new Buffer();
    buffer.writeUtf8("1");
    Reader reader = newReader(buffer.inputStream(), UTF_8);

    char[] chars = new char[1];
    int read = reader.read(chars, 0, chars.length);

    assertEquals(1, read);
    assertEquals('1', chars[0]);
  }

  @Test public void readAtEndOfStream() throws Exception {
    Reader reader = newReader(emptyInputStream(), UTF_8);

    char[] chars = new char[6];
    int read = reader.read(chars, 0, chars.length);

    assertEquals(-1, read);
  }

  @Test public void readBomRightBeforeEndOfStream() throws Exception {
    Buffer buffer = new Buffer();
    buffer.write(UTF_8_BOM);
    Reader reader = newReader(buffer.inputStream(), UTF_8);

    char[] chars = new char[6];
    int read = reader.read(chars, 0, chars.length);

    assertEquals(-1, read);
    // Make sure the BOM wasn't written to the array.
    assertEquals(0, chars[0]);
  }

  @Test public void readSingleCharacterRightBeforeEndOfStream() throws Exception {
    Buffer buffer = new Buffer();
    buffer.writeUtf8("x");
    Reader reader = newReader(buffer.inputStream(), UTF_8);

    char[] chars = new char[6];
    int read = reader.read(chars, 0, chars.length);

    assertEquals(1, read);
    assertEquals('x', chars[0]);
  }

  @Test public void readBomAndSingleCharacterRightBeforeEndOfStream() throws Exception {
    Buffer buffer = new Buffer();
    buffer.write(UTF_8_BOM);
    buffer.writeUtf8("x");
    Reader reader = newReader(buffer.inputStream(), UTF_8);

    char[] chars = new char[6];
    int read = reader.read(chars, 0, 1);

    assertEquals(1, read);
    assertEquals('x', chars[0]);
  }

  @Test public void readMoreCharactersThanAvailable() throws Exception {
    Buffer buffer = new Buffer();
    buffer.writeUtf8("hello");
    Reader reader = newReader(buffer.inputStream(), UTF_8);

    char[] chars = new char[6];
    int read = reader.read(chars, 0, chars.length);

    assertEquals(5, read);
    assertEquals("hello", new String(chars, 0, read));
  }

  @Test public void readTwice() throws Exception {
    Buffer buffer = new Buffer();
    buffer.writeUtf8("onetwo");
    Reader reader = newReader(buffer.inputStream(), UTF_8);

    char[] chars = new char[6];
    int read = reader.read(chars, 0, 3);

    assertEquals(3, read);
    assertEquals("one", new String(chars, 0, read));

    read = reader.read(chars, 3, 3);

    assertEquals(3, read);
    assertEquals("two", new String(chars, 3, read));

    assertEquals("onetwo", new String(chars, 0, 6));
  }

  @Test public void readLatin1() throws Exception {
    Charset latin1Charset = Charset.forName("ISO-8859-1");
    Buffer buffer = new Buffer();
    buffer.writeString("Überwachung", latin1Charset);
    Reader reader = newReader(buffer.inputStream(), latin1Charset);

    String stringFromReader = readerToString(reader);

    assertEquals("Überwachung", stringFromReader);
  }

  @Test public void closeClosesInputStream() throws Exception {
    MockInputStream inputStream = new MockInputStream();
    Reader reader = newReader(inputStream, null);

    reader.close();

    inputStream.assertClosed();
  }

  @Test public void toStringWithNullCharset() throws Exception {
    BomAwareReader reader = new BomAwareReader(emptyInputStream(), null, "$prefix");

    String result = reader.toString();

    assertEquals("$prefix.readerUtf8()", result);
  }

  @Test public void toStringWithUtf16Charset() throws Exception {
    Charset charset = Charset.forName("UTF-16");
    BomAwareReader reader = new BomAwareReader(emptyInputStream(), charset, "$prefix");

    String result = reader.toString();

    assertEquals("$prefix.reader(" + charset + ")", result);
  }

  private BomAwareReader newReader(InputStream inputStream, Charset charset) {
    return new BomAwareReader(inputStream, charset, "boring");
  }

  private InputStream emptyInputStream() {
    return new ByteArrayInputStream(new byte[0]);
  }

  static class MockInputStream extends InputStream {
    private boolean closed = false;

    @Override public int read() throws IOException {
      return 0;
    }

    @Override public void close() throws IOException {
      closed = true;
    }

    public void assertClosed() {
      assertTrue(closed);
    }
  }
}
