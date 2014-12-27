package okio;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static okio.TestUtil.assertByteArrayEquals;
import static okio.TestUtil.assertByteArraysEquals;
import static okio.TestUtil.repeat;
import static okio.Util.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class BufferedSourceTest {
  private interface Factory {
    BufferedSource create(Buffer data);
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return Arrays.asList(
        new Object[] { new Factory() {
          @Override public BufferedSource create(Buffer data) {
            return data;
          }

          @Override public String toString() {
            return "Buffer";
          }
        }},
        new Object[] { new Factory() {
          @Override public BufferedSource create(Buffer data) {
            return new RealBufferedSource(data);
          }

          @Override public String toString() {
            return "RealBufferedSource";
          }
        }}
    );
  }

  @Parameterized.Parameter
  public Factory factory;

  private Buffer data;
  private BufferedSource source;

  @Before public void setUp() {
    data = new Buffer();
    source = factory.create(data);
  }

  @Test public void readBytes() throws Exception {
    data.write(new byte[] { (byte) 0xab, (byte) 0xcd });
    assertEquals(0xab, source.readByte() & 0xff);
    assertEquals(0xcd, source.readByte() & 0xff);
    assertEquals(0, data.size());
  }

  @Test public void readShort() throws Exception {
    data.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x01
    });
    assertEquals((short) 0xabcd, source.readShort());
    assertEquals((short) 0xef01, source.readShort());
    assertEquals(0, data.size());
  }

  @Test public void readShortLe() throws Exception {
    data.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x10
    });
    assertEquals((short) 0xcdab, source.readShortLe());
    assertEquals((short) 0x10ef, source.readShortLe());
    assertEquals(0, data.size());
  }

  @Test public void readShortSplitAcrossMultipleSegments() throws Exception {
    data.writeUtf8(repeat('a', Segment.SIZE - 1));
    data.write(new byte[] { (byte) 0xab, (byte) 0xcd });
    source.skip(Segment.SIZE - 1);
    assertEquals((short) 0xabcd, source.readShort());
    assertEquals(0, data.size());
  }

  @Test public void readInt() throws Exception {
    data.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x01, (byte) 0x87, (byte) 0x65, (byte) 0x43,
        (byte) 0x21
    });
    assertEquals(0xabcdef01, source.readInt());
    assertEquals(0x87654321, source.readInt());
    assertEquals(0, data.size());
  }

  @Test public void readIntLe() throws Exception {
    data.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x10, (byte) 0x87, (byte) 0x65, (byte) 0x43,
        (byte) 0x21
    });
    assertEquals(0x10efcdab, source.readIntLe());
    assertEquals(0x21436587, source.readIntLe());
    assertEquals(0, data.size());
  }

  @Test public void readIntSplitAcrossMultipleSegments() throws Exception {
    data.writeUtf8(repeat('a', Segment.SIZE - 3));
    data.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x01
    });
    source.skip(Segment.SIZE - 3);
    assertEquals(0xabcdef01, source.readInt());
    assertEquals(0, data.size());
  }

  @Test public void readLong() throws Exception {
    data.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x10, (byte) 0x87, (byte) 0x65, (byte) 0x43,
        (byte) 0x21, (byte) 0x36, (byte) 0x47, (byte) 0x58, (byte) 0x69, (byte) 0x12, (byte) 0x23,
        (byte) 0x34, (byte) 0x45
    });
    assertEquals(0xabcdef1087654321L, source.readLong());
    assertEquals(0x3647586912233445L, source.readLong());
    assertEquals(0, data.size());
  }

  @Test public void readLongLe() throws Exception {
    data.write(new byte[]{
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x10, (byte) 0x87, (byte) 0x65, (byte) 0x43,
        (byte) 0x21, (byte) 0x36, (byte) 0x47, (byte) 0x58, (byte) 0x69, (byte) 0x12, (byte) 0x23,
        (byte) 0x34, (byte) 0x45
    });
    assertEquals(0x2143658710efcdabL, source.readLongLe());
    assertEquals(0x4534231269584736L, source.readLongLe());
    assertEquals(0, data.size());
  }

  @Test public void readLongSplitAcrossMultipleSegments() throws Exception {
    data.writeUtf8(repeat('a', Segment.SIZE - 7));
    data.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x01, (byte) 0x87, (byte) 0x65, (byte) 0x43,
        (byte) 0x21,
    });
    source.skip(Segment.SIZE - 7);
    assertEquals(0xabcdef0187654321L, source.readLong());
    assertEquals(0, data.size());
  }

  @Test public void readAll() throws IOException {
    source.buffer().writeUtf8("abc");
    data.writeUtf8("def");

    Buffer sink = new Buffer();
    assertEquals(6, source.readAll(sink));
    assertEquals("abcdef", sink.readUtf8());
    assertTrue(data.exhausted());
    assertTrue(source.exhausted());
  }

  @Test public void readAllExhausted() throws IOException {
    MockSink mockSink = new MockSink();
    assertEquals(0, source.readAll(mockSink));
    assertTrue(data.exhausted());
    assertTrue(source.exhausted());
    mockSink.assertLog();
  }

  @Test public void readExhaustedSource() throws Exception {
    Buffer sink = new Buffer();
    sink.writeUtf8(repeat('a', 10));
    assertEquals(-1, source.read(sink, 10));
    assertEquals(10, sink.size());
    assertEquals(0, data.size());
  }

  @Test public void readZeroBytesFromSource() throws Exception {
    Buffer sink = new Buffer();
    sink.writeUtf8(repeat('a', 10));

    // Either 0 or -1 is reasonable here. For consistency with Android's
    // ByteArrayInputStream we return 0.
    assertEquals(-1, source.read(sink, 0));
    assertEquals(10, sink.size());
    assertEquals(0, data.size());
  }

  @Test public void readFully() throws Exception {
    data.writeUtf8(repeat('a', 10000));
    Buffer sink = new Buffer();
    source.readFully(sink, 9999);
    assertEquals(repeat('a', 9999), sink.readUtf8());
    assertEquals("a", source.readUtf8());
  }

  @Test public void readFullyTooShortThrows() throws IOException {
    data.writeUtf8("Hi");
    Buffer sink = new Buffer();
    try {
      source.readFully(sink, 5);
      fail();
    } catch (EOFException ignored) {
    }

    // Verify we read all that we could from the source.
    assertEquals("Hi", sink.readUtf8());
  }

  @Test public void readFullyByteArray() throws IOException {
    data.writeUtf8("Hello").writeUtf8(repeat('e', Segment.SIZE));
    byte[] expected = data.clone().readByteArray();

    byte[] sink = new byte[Segment.SIZE + 5];
    source.readFully(sink);
    assertByteArraysEquals(expected, sink);
  }

  @Test public void readFullyByteArrayTooShortThrows() throws IOException {
    data.writeUtf8("Hello");

    byte[] sink = new byte[6];
    try {
      source.readFully(sink);
      fail();
    } catch (EOFException ignored) {
    }

    // Verify we read all that we could from the source.
    assertByteArraysEquals(new byte[]{'H', 'e', 'l', 'l', 'o', 0}, sink);
  }

  @Test public void readIntoByteArray() throws IOException {
    data.writeUtf8("abcd");

    byte[] sink = new byte[3];
    int read = source.read(sink);
    assertEquals(3, read);
    byte[] expected = { 'a', 'b', 'c' };
    assertByteArraysEquals(expected, sink);
  }

  @Test public void readIntoByteArrayNotEnough() throws IOException {
    data.writeUtf8("abcd");

    byte[] sink = new byte[5];
    int read = source.read(sink);
    assertEquals(4, read);
    byte[] expected = { 'a', 'b', 'c', 'd', 0 };
    assertByteArraysEquals(expected, sink);
  }

  @Test public void readIntoByteArrayOffsetAndCount() throws IOException {
    data.writeUtf8("abcd");

    byte[] sink = new byte[7];
    int read = source.read(sink, 2, 3);
    assertEquals(3, read);
    byte[] expected = { 0, 0, 'a', 'b', 'c', 0, 0 };
    assertByteArraysEquals(expected, sink);
  }

  @Test public void readByteArray() throws IOException {
    String string = "abcd" + repeat('e', Segment.SIZE);
    data.writeUtf8(string);
    assertByteArraysEquals(string.getBytes(UTF_8), source.readByteArray());
  }

  @Test public void readByteArrayPartial() throws IOException {
    data.writeUtf8("abcd");
    assertEquals("[97, 98, 99]", Arrays.toString(source.readByteArray(3)));
    assertEquals("d", source.readUtf8(1));
  }

  @Test public void readByteString() throws IOException {
    data.writeUtf8("abcd").writeUtf8(repeat('e', Segment.SIZE));
    assertEquals("abcd" + repeat('e', Segment.SIZE), source.readByteString().utf8());
  }

  @Test public void readByteStringPartial() throws IOException {
    data.writeUtf8("abcd").writeUtf8(repeat('e', Segment.SIZE));
    assertEquals("abc", source.readByteString(3).utf8());
    assertEquals("d", source.readUtf8(1));
  }

  @Test public void readSpecificCharsetPartial() throws Exception {
    data.write(ByteString.decodeHex("0000007600000259000002c80000006c000000e40000007300000259"
        + "000002cc000000720000006100000070000000740000025900000072"));
    assertEquals("vəˈläsə", source.readString(7 * 4, Charset.forName("utf-32")));
  }

  @Test public void readSpecificCharset() throws Exception {
    data.write(ByteString.decodeHex("0000007600000259000002c80000006c000000e40000007300000259"
        + "000002cc000000720000006100000070000000740000025900000072"));
    assertEquals("vəˈläsəˌraptər", source.readString(Charset.forName("utf-32")));
  }

  @Test public void readUtf8SpansSegments() throws Exception {
    data.writeUtf8(repeat('a', Segment.SIZE * 2));
    source.skip(Segment.SIZE - 1);
    assertEquals("aa", source.readUtf8(2));
  }

  @Test public void readUtf8Segment() throws Exception {
    data.writeUtf8(repeat('a', Segment.SIZE));
    assertEquals(repeat('a', Segment.SIZE), source.readUtf8(Segment.SIZE));
  }

  @Test public void readUtf8PartialBuffer() throws Exception {
    data.writeUtf8(repeat('a', Segment.SIZE + 20));
    assertEquals(repeat('a', Segment.SIZE + 10), source.readUtf8(Segment.SIZE + 10));
  }

  @Test public void readUtf8EntireBuffer() throws Exception {
    data.writeUtf8(repeat('a', Segment.SIZE * 2));
    assertEquals(repeat('a', Segment.SIZE * 2), source.readUtf8());
  }

  @Test public void skip() throws Exception {
    data.writeUtf8("a");
    data.writeUtf8(repeat('b', Segment.SIZE));
    data.writeUtf8("c");
    source.skip(1);
    assertEquals('b', source.readByte() & 0xff);
    source.skip(Segment.SIZE - 2);
    assertEquals('b', source.readByte() & 0xff);
    source.skip(1);
    assertTrue(source.exhausted());
  }

  @Test public void skipInsufficientData() throws Exception {
    data.writeUtf8("a");

    try {
      source.skip(2);
      fail();
    } catch (EOFException ignored) {
    }
  }

  @Test public void indexOf() throws Exception {
    // The segment is empty.
    assertEquals(-1, source.indexOf((byte) 'a'));

    // The segment has one value.
    data.writeUtf8("a"); // a
    assertEquals(0, source.indexOf((byte) 'a'));
    assertEquals(-1, source.indexOf((byte) 'b'));

    // The segment has lots of data.
    data.writeUtf8(repeat('b', Segment.SIZE - 2)); // ab...b
    assertEquals(0, source.indexOf((byte) 'a'));
    assertEquals(1, source.indexOf((byte) 'b'));
    assertEquals(-1, source.indexOf((byte) 'c'));

    // The segment doesn't start at 0, it starts at 2.
    source.skip(2); // b...b
    assertEquals(-1, source.indexOf((byte) 'a'));
    assertEquals(0, source.indexOf((byte) 'b'));
    assertEquals(-1, source.indexOf((byte) 'c'));

    // The segment is full.
    data.writeUtf8("c"); // b...bc
    assertEquals(-1, source.indexOf((byte) 'a'));
    assertEquals(0, source.indexOf((byte) 'b'));
    assertEquals(Segment.SIZE - 3, source.indexOf((byte) 'c'));

    // The segment doesn't start at 2, it starts at 4.
    source.skip(2); // b...bc
    assertEquals(-1, source.indexOf((byte) 'a'));
    assertEquals(0, source.indexOf((byte) 'b'));
    assertEquals(Segment.SIZE - 5, source.indexOf((byte) 'c'));

    // Two segments.
    data.writeUtf8("d"); // b...bcd, d is in the 2nd segment.
    assertEquals(Segment.SIZE - 4, source.indexOf((byte) 'd'));
    assertEquals(-1, source.indexOf((byte) 'e'));
  }

  @Test public void indexOfWithOffset() throws IOException {
    data.writeUtf8("a").writeUtf8(repeat('b', Segment.SIZE)).writeUtf8("c");
    assertEquals(-1, source.indexOf((byte) 'a', 1));
    assertEquals(15, source.indexOf((byte) 'b', 15));
  }

  @Test public void indexOfElement() throws IOException {
    data.writeUtf8("a").writeUtf8(repeat('b', Segment.SIZE)).writeUtf8("c");
    assertEquals(0, source.indexOfElement(ByteString.encodeUtf8("DEFGaHIJK")));
    assertEquals(1, source.indexOfElement(ByteString.encodeUtf8("DEFGHIJKb")));
    assertEquals(Segment.SIZE + 1, source.indexOfElement(ByteString.encodeUtf8("cDEFGHIJK")));
    assertEquals(1, source.indexOfElement(ByteString.encodeUtf8("DEFbGHIc")));
    assertEquals(-1L, source.indexOfElement(ByteString.encodeUtf8("DEFGHIJK")));
    assertEquals(-1L, source.indexOfElement(ByteString.encodeUtf8("")));
  }

  @Test public void indexOfElementWithOffset() throws IOException {
    data.writeUtf8("a").writeUtf8(repeat('b', Segment.SIZE)).writeUtf8("c");
    assertEquals(-1, source.indexOfElement(ByteString.encodeUtf8("DEFGaHIJK"), 1));
    assertEquals(15, source.indexOfElement(ByteString.encodeUtf8("DEFGHIJKb"), 15));
  }

  @Test public void request() throws IOException {
    data.writeUtf8("a").writeUtf8(repeat('b', Segment.SIZE)).writeUtf8("c");
    assertTrue(source.request(Segment.SIZE + 2));
    assertFalse(source.request(Segment.SIZE + 3));
  }

  @Test public void require() throws IOException {
    data.writeUtf8("a").writeUtf8(repeat('b', Segment.SIZE)).writeUtf8("c");
    source.require(Segment.SIZE + 2);
    try {
      source.require(Segment.SIZE + 3);
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void inputStream() throws Exception {
    data.writeUtf8("abc");
    InputStream in = source.inputStream();
    byte[] bytes = new byte[3];
    int read = in.read(bytes);
    assertEquals(3, read);
    assertByteArrayEquals("abc", bytes);
    assertEquals(-1, in.read());
  }

  @Test public void inputStreamOffsetCount() throws Exception {
    data.writeUtf8("abcde");
    InputStream in = source.inputStream();
    byte[] bytes = { 'z', 'z', 'z', 'z', 'z' };
    int read = in.read(bytes, 1, 3);
    assertEquals(3, read);
    assertByteArrayEquals("zabcz", bytes);
  }

  @Test public void inputStreamSkip() throws Exception {
    data.writeUtf8("abcde");
    InputStream in = source.inputStream();
    assertEquals(4, in.skip(4));
    assertEquals('e', in.read());
  }

  @Test public void inputStreamCharByChar() throws Exception {
    data.writeUtf8("abc");
    InputStream in = source.inputStream();
    assertEquals('a', in.read());
    assertEquals('b', in.read());
    assertEquals('c', in.read());
    assertEquals(-1, in.read());
  }

  @Test public void inputStreamBounds() throws IOException {
    data.writeUtf8(repeat('a', 100));
    InputStream in = source.inputStream();
    try {
      in.read(new byte[100], 50, 51);
      fail();
    } catch (ArrayIndexOutOfBoundsException expected) {
    }
  }
}
