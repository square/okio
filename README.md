Okio
====

See the [project website][okio] for documentation and APIs.

Okio is a library that complements `java.io` and `java.nio` to make it much
easier to access, store, and process your data. It started as a component of
[OkHttp][1], the capable HTTP client included in Android. It's well-exercised
and ready to solve new problems.

ByteStrings and Buffers
-----------------------

Okio is built around two types that pack a lot of capability into a
straightforward API:

 * [**ByteString**][3] is an immutable sequence of bytes. For character data, `String`
   is fundamental. `ByteString` is String's long-lost brother, making it easy to
   treat binary data as a value. This class is ergonomic: it knows how to encode
   and decode itself as hex, base64, and UTF-8.

 * [**Buffer**][4] is a mutable sequence of bytes. Like `ArrayList`, you don't need
   to size your buffer in advance. You read and write buffers as a queue: write
   data to the end and read it from the front. There's no obligation to manage
   positions, limits, or capacities.

Internally, `ByteString` and `Buffer` do some clever things to save CPU and
memory. If you encode a UTF-8 string as a `ByteString`, it caches a reference to
that string so that if you decode it later, there's no work to do.

`Buffer` is implemented as a linked list of segments. When you move data from
one buffer to another, it _reassigns ownership_ of the segments rather than
copying the data across. This approach is particularly helpful for multithreaded
programs: a thread that talks to the network can exchange data with a worker
thread without any copying or ceremony.

Sources and Sinks
-----------------

An elegant part of the `java.io` design is how streams can be layered for
transformations like encryption and compression. Okio includes its own stream
types called [`Source`][5] and [`Sink`][6] that work like `InputStream` and
`OutputStream`, but with some key differences:

 * **Timeouts.** The streams provide access to the timeouts of the underlying
   I/O mechanism. Unlike the `java.io` socket streams, both `read()` and
   `write()` calls honor timeouts.

 * **Easy to implement.** `Source` declares three methods: `read()`, `close()`,
   and `timeout()`. There are no hazards like `available()` or single-byte reads
   that cause correctness and performance surprises.

 * **Easy to use.** Although _implementations_ of `Source` and `Sink` have only
   three methods to write, _callers_ are given a rich API with the
   [`BufferedSource`][7] and [`BufferedSink`][8] interfaces. These interfaces give you
   everything you need in one place.

 * **No artificial distinction between byte streams and char streams.** It's all
   data. Read and write it as bytes, UTF-8 strings, big-endian 32-bit integers,
   little-endian shorts; whatever you want. No more `InputStreamReader`!

 * **Easy to test.** The `Buffer` class implements both `BufferedSource` and
   `BufferedSink` so your test code is simple and clear.

Sources and sinks interoperate with `InputStream` and `OutputStream`. You can
view any `Source` as an `InputStream`, and you can view any `InputStream` as a
`Source`. Similarly for `Sink` and `OutputStream`.


Presentations
-------------

[A Few “Ok” Libraries][ok_libraries_talk] ([slides][ok_libraries_slides]): An introduction to Okio
and three libraries written with it.

[Decoding the Secrets of Binary Data][encoding_talk] ([slides][encoding_slides]): How data encoding
works and how Okio does it.

[Ok Multiplatform!][ok_multiplatform_talk] ([slides][ok_multiplatform_slides]): How we changed
Okio’s implementation language from Java to Kotlin.


Recipes
-------

We've written some recipes that demonstrate how to solve common problems with
Okio. Read through them to learn about how everything works together.
Cut-and-paste these examples freely; that's what they're for.

### [Read a text file line-by-line][ReadFileLineByLine]

Use `Okio.source(File)` to open a source stream to read a file. The returned
`Source` interface is very small and has limited uses. Instead we wrap the
source with a buffer. This has two benefits:

 * **It makes the API more powerful.** Instead of the basic methods offered by
   `Source`, `BufferedSource` has dozens of methods to address most common
   problems concisely.

 * **It makes your program run faster.** Buffering allows Okio to get more done
   with fewer I/O operations.

Each `Source` that is opened needs to be closed. The code that opens the stream
is responsible for making sure it is closed. Here we use Java's `try` blocks to
close our sources automatically.

```java
public void readLines(File file) throws IOException {
  try (Source fileSource = Okio.source(file);
       BufferedSource bufferedSource = Okio.buffer(fileSource)) {

    while (true) {
      String line = bufferedSource.readUtf8Line();
      if (line == null) break;

      if (line.contains("square")) {
        System.out.println(line);
      }
    }

  }
}
```

The `readUtf8Line()` API reads all of the data until the next line delimiter –
either `\n`, `\r\n`, or the end of the file. It returns that data as a string,
omitting the delimiter at the end. When it encounters empty lines the method
will return an empty string. If there isn’t any more data to read it will
return null.

The above program can be written more compactly by inlining the `fileSource`
variable and by using a fancy `for` loop instead of a `while`:

```java
public void readLines(File file) throws IOException {
  try (BufferedSource source = Okio.buffer(Okio.source(file))) {
    for (String line; (line = source.readUtf8Line()) != null; ) {
      if (line.contains("square")) {
        System.out.println(line);
      }
    }
  }
}
```

The `readUtf8Line()` method is suitable for parsing most files. For certain
use-cases you may also consider `readUtf8LineStrict()`. It is similar but it
requires that each line is terminated by `\n` or `\r\n`. If it encounters the
end of the file before that it will throw an `EOFException`. The strict variant
also permits a byte limit to defend against malformed input.

```java
public void readLines(File file) throws IOException {
  try (BufferedSource source = Okio.buffer(Okio.source(file))) {
    while (!source.exhausted()) {
      String line = source.readUtf8LineStrict(1024L);
      if (line.contains("square")) {
        System.out.println(line);
      }
    }
  }
}
```

### [Write a text file][WriteFile]

Above we used a `Source` and a `BufferedSource` to read a file. To write, we use
a `Sink` and a `BufferedSink`. The advantages of buffering are the same: a more
capable API and better performance.

```java
public void writeEnv(File file) throws IOException {
  try (Sink fileSink = Okio.sink(file);
       BufferedSink bufferedSink = Okio.buffer(fileSink)) {

    for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
      bufferedSink.writeUtf8(entry.getKey());
      bufferedSink.writeUtf8("=");
      bufferedSink.writeUtf8(entry.getValue());
      bufferedSink.writeUtf8("\n");
    }

  }
}
```

There isn’t an API to write a line of input; instead we manually insert our own
newline character. Most programs should hardcode `"\n"` as the newline
character. In rare situations you may use `System.lineSeparator()` instead of
`"\n"`: it returns `"\r\n"` on Windows and `"\n"` everywhere else.

We can write the above program more compactly by inlining the `fileSink`
variable and by taking advantage of method chaining:

```java
public void writeEnv(File file) throws IOException {
  try (BufferedSink sink = Okio.buffer(Okio.sink(file))) {
    for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
      sink.writeUtf8(entry.getKey())
          .writeUtf8("=")
          .writeUtf8(entry.getValue())
          .writeUtf8("\n");
    }
  }
}
```

In the above code we make four calls to `writeUtf8()`. Making four calls is
more efficient than the code below because the VM doesn’t have to create and
garbage collect a temporary string.

```java
sink.writeUtf8(entry.getKey() + "=" + entry.getValue() + "\n"); // Slower!
```

### [UTF-8][ExploreCharsets]

In the above APIs you can see that Okio really likes UTF-8. Early computer
systems suffered many incompatible character encodings: ISO-8859-1, ShiftJIS,
ASCII, EBCDIC, etc. Writing software to support multiple character sets was
awful and we didn’t even have emoji! Today we're lucky that the world has
standardized on UTF-8 everywhere, with some rare uses of other charsets in
legacy systems.

If you need another character set, `readString()` and `writeString()` are there
for you. These methods require that you specify a character set. Otherwise you
may accidentally create data that is only readable by the local computer. Most
programs should use the UTF-8 methods only.

When encoding strings you need to be mindful of the different ways that strings
are represented and encoded. When a glyph has an accent or another adornment
it may be represented as a single complex code point (`é`) or as a simple code
point (`e`) followed by its modifiers (`´`). When the entire glyph is a single
code point that’s called [NFC][nfc]; when it’s multiple it’s [NFD][nfd].

Though we use UTF-8 whenever we read or write strings in I/O, when they are in
memory Java Strings use an obsolete character encoding called UTF-16. It is a
bad encoding because it uses a 16-bit `char` for most characters, but some don’t
fit. In particular, most emoji use two Java chars. This is problematic because
`String.length()` returns a surprising result: the number of UTF-16 chars and
not the natural number of glyphs.

|                       | Café 🍩                     | Café 🍩                        |
| --------------------: | :---------------------------| :------------------------------|
|                  Form | [NFC][nfc]                  | [NFD][nfd]                     |
|           Code Points | `c  a  f  é    ␣   🍩     ` | `c  a  f  e  ´    ␣   🍩     ` |
|           UTF-8 bytes | `43 61 66 c3a9 20 f09f8da9` | `43 61 66 65 cc81 20 f09f8da9` |
| String.codePointCount | 6                           | 7                              |
|         String.length | 7                           | 8                              |
|             Utf8.size | 10                          | 11                             |

For the most part Okio lets you ignore these problems and focus on your data.
But when you need them, there are convenient APIs for dealing with low-level
UTF-8 strings.

Use `Utf8.size()` to count the number of bytes required to encode a string as
UTF-8 without actually encoding it. This is handy in length-prefixed encodings
like protocol buffers.

Use `BufferedSource.readUtf8CodePoint()` to read a single variable-length code
point, and `BufferedSink.writeUtf8CodePoint()` to write one.

### [Golden Values][GoldenValue]

Okio likes testing. The library itself is heavily tested, and it has features
that are often helpful when testing application code. One pattern we’ve found to
be quite useful is “golden value” testing. The goal of such tests is to confirm
that data encoded with earlier versions of a program can safely be decoded by
the current program.

We’ll illustrate this by encoding a value using Java Serialization. Though we
must disclaim that Java Serialization is an awful encoding system and most
programs should prefer other formats like JSON or protobuf! In any case, here’s
a method that takes an object, serializes it, and returns the result as a
`ByteString`:

```java
private ByteString serialize(Object o) throws IOException {
  Buffer buffer = new Buffer();
  try (ObjectOutputStream objectOut = new ObjectOutputStream(buffer.outputStream())) {
    objectOut.writeObject(o);
  }
  return buffer.readByteString();
}
```

There’s a lot going on here.

1. We create a buffer as a holding space for our serialized data. It’s a convenient
   replacement for `ByteArrayOutputStream`.

2. We ask the buffer for its output stream. Writes to a buffer or its output stream
   always append data to the end of the buffer.

3. We create an `ObjectOutputStream` (the encoding API for Java serialization) and
   write our object. The try block takes care of closing the stream for us. Note
   that closing a buffer has no effect.

4. Finally we read a byte string from the buffer. The `readByteString()` method
   allows us to specify how many bytes to read; here we don’t specify a count in
   order to read the entire thing. Reads from a buffer always consume data from
   the front of the buffer.

With our `serialize()` method handy we are ready to compute and print a golden
value.

```java
Point point = new Point(8.0, 15.0);
ByteString pointBytes = serialize(point);
System.out.println(pointBytes.base64());
```

We print the `ByteString` as [base64][base64] because it’s a compact format
that’s suitable for embedding in a test case. The program prints this:

```
rO0ABXNyAB5va2lvLnNhbXBsZXMuR29sZGVuVmFsdWUkUG9pbnTdUW8rMji1IwIAAkQAAXhEAAF5eHBAIAAAAAAAAEAuAAAAAAAA
```

That’s our golden value! We can embed it in our test case using base64 again
to convert it back into a `ByteString`:

```java
ByteString goldenBytes = ByteString.decodeBase64("rO0ABXNyAB5va2lvLnNhbXBsZ"
    + "XMuR29sZGVuVmFsdWUkUG9pbnTdUW8rMji1IwIAAkQAAXhEAAF5eHBAIAAAAAAAAEAuA"
    + "AAAAAAA");
```

The next step is to deserialize the `ByteString` back into our value class. This
method reverses the `serialize()` method above: we append a byte string to a
buffer then consume it using an `ObjectInputStream`:

```java
private Object deserialize(ByteString byteString) throws IOException, ClassNotFoundException {
  Buffer buffer = new Buffer();
  buffer.write(byteString);
  try (ObjectInputStream objectIn = new ObjectInputStream(buffer.inputStream())) {
    return objectIn.readObject();
  }
}
```

Now we can test the decoder against the golden value:

```java
ByteString goldenBytes = ByteString.decodeBase64("rO0ABXNyAB5va2lvLnNhbXBsZ"
    + "XMuR29sZGVuVmFsdWUkUG9pbnTdUW8rMji1IwIAAkQAAXhEAAF5eHBAIAAAAAAAAEAuA"
    + "AAAAAAA");
Point decoded = (Point) deserialize(goldenBytes);
assertEquals(new Point(8.0, 15.0), decoded);
```

With this test we can change the serialization of the `Point` class without
breaking compatibility.


### [Write a binary file][BitmapEncoder]

Encoding a binary file is not unlike encoding a text file. Okio uses the same
`BufferedSink` and `BufferedSource` bytes for both. This is handy for binary
formats that include both byte and character data.

Writing binary data is more hazardous than text because if you make a mistake it
is often quite difficult to diagnose. Avoid such mistakes by being careful
around these traps:

 * **The width of each field.** This is the number of bytes used. Okio doesn't
   include a mechanism to emit partial bytes. If you need that, you’ll need to
   do your own bit shifting and masking before writing.

 * **The endianness of each field.** All fields that have more than one byte
   have _endianness_: whether the bytes are ordered most-significant to least
   (big endian) or least-significant to most (little endian). Okio uses the `Le`
   suffix for little-endian methods; methods without a suffix are big-endian.

 * **Signed vs. Unsigned.** Java doesn’t have unsigned primitive types (except
   for `char`!) so coping with this is often something that happens at the
   application layer. To make this a little easier Okio accepts `int` types for
   `writeByte()` and `writeShort()`. You can pass an “unsigned” byte like 255
   and Okio will do the right thing.

| Method       | Width | Endianness |           Value | Encoded Value             |
| :----------- | ----: | :--------- | --------------: | :------------------------ |
| writeByte    |     1 |            |               3 | `03`                      |
| writeShort   |     2 | big        |               3 | `00 03`                   |
| writeInt     |     4 | big        |               3 | `00 00 00 03`             |
| writeLong    |     8 | big        |               3 | `00 00 00 00 00 00 00 03` |
| writeShortLe |     2 | little     |               3 | `03 00`                   |
| writeIntLe   |     4 | little     |               3 | `03 00 00 00`             |
| writeLongLe  |     8 | little     |               3 | `03 00 00 00 00 00 00 00` |
| writeByte    |     1 |            |  Byte.MAX_VALUE | `7f`                      |
| writeShort   |     2 | big        | Short.MAX_VALUE | `7f ff`                   |
| writeInt     |     4 | big        |   Int.MAX_VALUE | `7f ff ff ff`             |
| writeLong    |     8 | big        |  Long.MAX_VALUE | `7f ff ff ff ff ff ff ff` |
| writeShortLe |     2 | little     | Short.MAX_VALUE | `ff 7f`                   |
| writeIntLe   |     4 | little     |   Int.MAX_VALUE | `ff ff ff 7f`             |
| writeLongLe  |     8 | little     |  Long.MAX_VALUE | `ff ff ff ff ff ff ff 7f` |

This code encodes a bitmap following the [BMP file format][bmp].

```java
void encode(Bitmap bitmap, BufferedSink sink) throws IOException {
  int height = bitmap.height();
  int width = bitmap.width();

  int bytesPerPixel = 3;
  int rowByteCountWithoutPadding = (bytesPerPixel * width);
  int rowByteCount = ((rowByteCountWithoutPadding + 3) / 4) * 4;
  int pixelDataSize = rowByteCount * height;
  int bmpHeaderSize = 14;
  int dibHeaderSize = 40;

  // BMP Header
  sink.writeUtf8("BM"); // ID.
  sink.writeIntLe(bmpHeaderSize + dibHeaderSize + pixelDataSize); // File size.
  sink.writeShortLe(0); // Unused.
  sink.writeShortLe(0); // Unused.
  sink.writeIntLe(bmpHeaderSize + dibHeaderSize); // Offset of pixel data.

  // DIB Header
  sink.writeIntLe(dibHeaderSize);
  sink.writeIntLe(width);
  sink.writeIntLe(height);
  sink.writeShortLe(1);  // Color plane count.
  sink.writeShortLe(bytesPerPixel * Byte.SIZE);
  sink.writeIntLe(0);    // No compression.
  sink.writeIntLe(16);   // Size of bitmap data including padding.
  sink.writeIntLe(2835); // Horizontal print resolution in pixels/meter. (72 dpi).
  sink.writeIntLe(2835); // Vertical print resolution in pixels/meter. (72 dpi).
  sink.writeIntLe(0);    // Palette color count.
  sink.writeIntLe(0);    // 0 important colors.

  // Pixel data.
  for (int y = height - 1; y >= 0; y--) {
    for (int x = 0; x < width; x++) {
      sink.writeByte(bitmap.blue(x, y));
      sink.writeByte(bitmap.green(x, y));
      sink.writeByte(bitmap.red(x, y));
    }

    // Padding for 4-byte alignment.
    for (int p = rowByteCountWithoutPadding; p < rowByteCount; p++) {
      sink.writeByte(0);
    }
  }
}
```

The trickiest part of this program is the format’s required padding. The BMP
format expects each row to begin on a 4-byte boundary so it is necessary to add
zeros to maintain the alignment.

Encoding other binary formats is usually quite similar. Some tips:

 * Write tests with golden values! Confirming that your program emits the
   expected result can make debugging easier.
 * Use `Utf8.size()` to compute the number of bytes of an encoded string. This
   is essential for length-prefixed formats.
 * Use `Float.floatToIntBits()` and `Double.doubleToLongBits()` to encode
   floating point values.


### [Communicate on a Socket][SocksProxyServer]

Sending and receiving data over the network is a bit like writing and reading
files. We use `BufferedSink` to encode output and `BufferedSource` to decode
input. Like files, network protocols can be text, binary, or a mix of both. But
there are also some substantial differences between the network and the
filesystem.

With a file you’re either reading or writing but with the network you can do
both! Some protocols handle this by taking turns: write a request, read a
response, repeat. You can implement this kind of protocol with a single thread.
In other protocols you may read and write simultaneously. Typically you’ll want
one dedicated thread for reading. For writing you can use either a dedicated
thread or use `synchronized` so that multiple threads can share a sink. Okio’s
streams are not safe for concurrent use.

Sinks buffer outbound data to minimize I/O operations. This is efficient but it
means you must manually call `flush()` to transmit data. Typically
message-oriented protocols flush after each message. Note that Okio will
automatically flush when the buffered data exceeds some threshold. This is
intended to save memory and you shouldn’t rely on it for interactive protocols.

Okio builds on `java.io.Socket` for connectivity. Create your socket as a server
or as a client, then use `Okio.source(Socket)` to read and `Okio.sink(Socket)`
to write. These APIs also work with `SSLSocket`. You should use SSL unless you
have a very good reason not to!

Cancel a socket from any thread by calling `Socket.close()`; this will cause its
sources and sinks to immediately fail with an `IOException`. You can also
configure timeouts for all socket operations. You don’t need a reference to the
socket to adjust timeouts: `Source` and `Sink` expose timeouts directly. This
API works even if the streams are decorated.

As a complete example of networking with Okio we wrote a [basic SOCKS
proxy][SocksProxyServer] server. Some highlights:

```java
Socket fromSocket = ...
BufferedSource fromSource = Okio.buffer(Okio.source(fromSocket));
BufferedSink fromSink = Okio.buffer(Okio.sink(fromSocket));
```

Creating sources and sinks for sockets is the same as creating them for files.
Once you create a `Source` or `Sink` for a socket you must not use its
`InputStream` or `OutputStream`, respectively.

```java
Buffer buffer = new Buffer();
for (long byteCount; (byteCount = source.read(buffer, 8192L)) != -1; ) {
  sink.write(buffer, byteCount);
  sink.flush();
}
```

The above loop copies data from the source to the sink, flushing after each
read. If we didn’t need the flushing we could replace this loop with a single
call to `BufferedSink.writeAll(Source)`.

The `8192` argument to `read()` is the maximum number of bytes to read before
returning. We could have passed any value here, but we like 8 KiB because that’s
the largest value Okio can do in a single system call. Most of the time
application code doesn’t need to deal with such limits!

```java
int addressType = fromSource.readByte() & 0xff;
int port = fromSource.readShort() & 0xffff;
```

Okio uses signed types like `byte` and `short`, but often protocols want
unsigned values. The bitwise `&` operator is Java’s preferred idiom to convert
a signed value into an unsigned value. Here’s a cheat sheet for bytes, shorts,
and ints:

| Type  | Signed Range                  | Unsigned Range   | Signed to Unsigned          |
| :---- | :---------------------------: | :--------------- | :-------------------------- |
| byte  | -128..127                     | 0..255           | `int u = s & 0xff;`         |
| short | -32,768..32,767               | 0..65,535        | `int u = s & 0xffff;`       |
| int   | -2,147,483,648..2,147,483,647 | 0..4,294,967,295 | `long u = s & 0xffffffffL;` |

Java has no primitive type that can represent unsigned longs.


### [Hashing][Hashing]

We’re bombarded by hashing in our lives as Java programmers. Early on we're introduced to the
`hashCode()` method, something we know we need to override otherwise unforeseen bad things happen.
Later we’re shown `LinkedHashMap` and its friends. These build on that `hashCode()` method to
organize data for fast retrieval.

Elsewhere we have cryptographic hash functions. These get used all over the place. HTTPS
certificates, Git commits, BitTorrent integrity checking, and Blockchain blocks all use
cryptographic hashes. Good use of hashes can improve the performance, privacy, security, and
simplicity of an application.

Each cryptographic hash function accepts a variable-length stream of input bytes and produces a
fixed-length byte string value called the “hash”. Hash functions have these important qualities:

 * Deterministic: each input always produces the same output.
 * Uniform: each output byte string is equally likely. It is very difficult to find or create pairs
   of different inputs that yield the same output. This is called a “collision”.
 * Non-reversible: knowing an output doesn't help you to find the input. Note that if you know some
   possible inputs you can hash them to see if their hashes match.
 * Well-known: the hash is implemented everywhere and rigorously understood.

Good hash functions are very cheap to compute (dozens of microseconds) and expensive to reverse
(quintillions of millenia). Steady advances in computing and mathematics have caused once-great hash
functions to become inexpensive to reverse. When choosing a hash function, beware that not all are
created equal! Okio supports these well-known cryptographic hash functions:

 * **MD5**: a 128-bit (16 byte) cryptographic hash. It is both insecure and obsolete because it is
   inexpensive to reverse! This hash is offered because it is popular and convenient for use in
   legacy systems that are not security-sensitive.
 * **SHA-1**: a 160-bit (20 byte) cryptographic hash. It was recently demonstrated that it is
   feasible to create SHA-1 collisions. Consider upgrading from SHA-1 to SHA-256.
 * **SHA-256**: a 256-bit (32 byte) cryptographic hash. SHA-256 is widely understood and expensive
   to reverse. This is the hash most systems should use.
 * **SHA-512**: a 512-bit (64 byte) cryptographic hash. It is expensive to reverse.

Each hash creates a `ByteString` of the specified length. Use `hex()` to get the conventional
human-readable form. Or leave it as a `ByteString` because that’s a convenient model type!

Okio can produce cryptographic hashes from byte strings:

```
ByteString byteString = readByteString(new File("README.md"));
System.out.println("   md5: " + byteString.md5().hex());
System.out.println("  sha1: " + byteString.sha1().hex());
System.out.println("sha256: " + byteString.sha256().hex());
System.out.println("sha512: " + byteString.sha512().hex());
```

From buffers:

```
Buffer buffer = readBuffer(new File("README.md"));
System.out.println("   md5: " + buffer.md5().hex());
System.out.println("  sha1: " + buffer.sha1().hex());
System.out.println("sha256: " + buffer.sha256().hex());
System.out.println("sha512: " + buffer.sha512().hex());
```

While streaming from a source:

```
try (HashingSink hashingSink = HashingSink.sha256(Okio.blackhole());
     BufferedSource source = Okio.buffer(Okio.source(file))) {
  source.readAll(hashingSink);
  System.out.println("sha256: " + hashingSink.hash().hex());
}
```

While streaming to a sink:

```
try (HashingSink hashingSink = HashingSink.sha256(Okio.blackhole());
     BufferedSink sink = Okio.buffer(hashingSink);
     Source source = Okio.source(file)) {
  sink.writeAll(source);
  sink.close(); // Emit anything buffered.
  System.out.println("sha256: " + hashingSink.hash().hex());
}
```

Okio also supports HMAC (Hash Message Authentication Code) which combines a secret and a hash.
Applications use HMAC for data integrity and authentication.

```
ByteString secret = ByteString.decodeHex("7065616e7574627574746572");
System.out.println("hmacSha256: " + byteString.hmacSha256(secret).hex());
```

As with hashing, you can generate an HMAC from a `ByteString`, `Buffer`, `HashingSource`, and
`HashingSink`. Note that Okio doesn’t implement HMAC for MD5. Okio uses Java’s
`java.security.MessageDigest` for cryptographic hashes and `javax.crypto.Mac` for HMAC.


Releases
--------

Our [change log][changelog] has release history.

```kotlin
implementation("com.squareup.okio:okio:2.6.0")
```

<details>
   <summary>Snapshot builds are also available</summary>
      
```kotlin
repositories {
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}
      
dependencies {
   implementation("com.squareup.okio:okio:2.6.0")
}
```   
  
</details>


R8 / ProGuard
--------

If you are using R8 or ProGuard add the options from [this file][proguard].


License
--------

    Copyright 2013 Square, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
    
 [1]: https://github.com/square/okhttp
 [3]: https://square.github.io/okio/2.x/okio/okio/-byte-string/index.html
 [4]: https://square.github.io/okio/2.x/okio/okio/-buffer/index.html
 [5]: https://square.github.io/okio/2.x/okio/okio/-source/index.html
 [6]: https://square.github.io/okio/2.x/okio/okio/-sink/index.html
 [7]: https://square.github.io/okio/2.x/okio/okio/-buffered-source/index.html
 [8]: https://square.github.io/okio/2.x/okio/okio/-buffered-sink/index.html
 [changelog]: http://square.github.io/okio/changelog/
 [javadoc]: https://square.github.io/okio/2.x/okio/okio/index.html
 [nfd]: https://docs.oracle.com/javase/7/docs/api/java/text/Normalizer.Form.html#NFD
 [nfc]: https://docs.oracle.com/javase/7/docs/api/java/text/Normalizer.Form.html#NFC
 [base64]: https://tools.ietf.org/html/rfc4648#section-4
 [bmp]: https://en.wikipedia.org/wiki/BMP_file_format
 [ok_libraries_talk]: https://www.youtube.com/watch?v=WvyScM_S88c
 [ok_libraries_slides]: https://speakerdeck.com/jakewharton/a-few-ok-libraries-droidcon-mtl-2015
 [okio]: https://square.github.io/okio/
 [encoding_talk]: https://www.youtube.com/watch?v=T_p22jMZSrk
 [encoding_slides]: https://speakerdeck.com/swankjesse/decoding-the-secrets-of-binary-data-droidcon-nyc-2016
 [ok_multiplatform_talk]: https://www.youtube.com/watch?v=Q8B4eDirgk0
 [ok_multiplatform_slides]: https://speakerdeck.com/swankjesse/ok-multiplatform
 [ReadFileLineByLine]: https://github.com/square/okio/blob/master/samples/src/jvmMain/java/okio/samples/ReadFileLineByLine.java
 [WriteFile]: https://github.com/square/okio/blob/master/samples/src/jvmMain/java/okio/samples/WriteFile.java
 [ExploreCharsets]: https://github.com/square/okio/blob/master/samples/src/jvmMain/java/okio/samples/ExploreCharsets.java
 [GoldenValue]: https://github.com/square/okio/blob/master/samples/src/jvmMain/java/okio/samples/GoldenValue.java
 [BitmapEncoder]: https://github.com/square/okio/blob/master/samples/src/jvmMain/java/okio/samples/BitmapEncoder.java
 [SocksProxyServer]: https://github.com/square/okio/blob/master/samples/src/jvmMain/java/okio/samples/SocksProxyServer.java
 [Hashing]: https://github.com/square/okio/blob/master/samples/src/jvmMain/java/okio/samples/Hashing.java
 [proguard]: https://github.com/square/okio/blob/master/okio/src/jvmMain/resources/META-INF/proguard/okio.pro
