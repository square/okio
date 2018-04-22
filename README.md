Okio
====

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

Recipes
-------

We've written some recipes that demonstrate how to solve common problems with
Okio. Read through them to learn about how everything works together.
Cut-and-paste these examples freely; that's what they're for.

### [Read a text file line-by-line](https://github.com/square/okio/blob/master/samples/src/main/java/okio/samples/ReadFileLineByLine.java)

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

### [Write a text file](https://github.com/square/okio/blob/master/samples/src/main/java/okio/samples/WriteFile.java)

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

### [UTF-8](https://github.com/square/okio/blob/master/samples/src/main/java/okio/samples/ExploreCharsets.java)

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

|                String | Café 🍩                     | Café 🍩                        |
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

### PNG decoder

Here we decode the chunks of a PNG file.

```java
private static final ByteString PNG_HEADER = ByteString.decodeHex("89504e470d0a1a0a");

public void decodePng(InputStream in) throws IOException {
  try (BufferedSource pngSource = Okio.buffer(Okio.source(in))) {
    ByteString header = pngSource.readByteString(PNG_HEADER.size());
    if (!header.equals(PNG_HEADER)) {
      throw new IOException("Not a PNG.");
    }

    while (true) {
      Buffer chunk = new Buffer();

      // Each chunk is a length, type, data, and CRC offset.
      int length = pngSource.readInt();
      String type = pngSource.readUtf8(4);
      pngSource.readFully(chunk, length);
      int crc = pngSource.readInt();

      decodeChunk(type, chunk);
      if (type.equals("IEND")) break;
    }
  }
}

private void decodeChunk(String type, Buffer chunk) {
  if (type.equals("IHDR")) {
    int width = chunk.readInt();
    int height = chunk.readInt();
    System.out.printf("%08x: %s %d x %d%n", chunk.size(), type, width, height);
  } else {
    System.out.printf("%08x: %s%n", chunk.size(), type);
  }
}
```

Download
--------

Download [the latest JAR][2] or grab via Maven:
```xml
<dependency>
    <groupId>com.squareup.okio</groupId>
    <artifactId>okio</artifactId>
    <version>1.14.0</version>
</dependency>
```
or Gradle:
```groovy
compile 'com.squareup.okio:okio:1.14.0'
```

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

ProGuard
--------

If you are using ProGuard you might need to add the following option:
```
-dontwarn okio.**
```

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
 [2]: https://search.maven.org/remote_content?g=com.squareup.okio&a=okio&v=LATEST
 [3]: https://square.github.io/okio/1.x/okio/okio/ByteString.html
 [4]: https://square.github.io/okio/1.x/okio/okio/Buffer.html
 [5]: https://square.github.io/okio/1.x/okio/okio/Source.html
 [6]: https://square.github.io/okio/1.x/okio/okio/Sink.html
 [7]: https://square.github.io/okio/1.x/okio/okio/BufferedSource.html
 [8]: https://square.github.io/okio/1.x/okio/okio/BufferedSink.html
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/
 [javadoc]: http://square.github.io/okio/1.x/okio
 [nfd]: https://docs.oracle.com/javase/7/docs/api/java/text/Normalizer.Form.html#NFD
 [nfc]: https://docs.oracle.com/javase/7/docs/api/java/text/Normalizer.Form.html#NFC