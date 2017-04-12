Okio
====

Okio is a new library that complements `java.io` and `java.nio` to make it much
easier to access, store, and process your data.

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

Dependable
----------

Okio started as a component of [OkHttp][1], the capable HTTP+SPDY client
included in Android. It's well-exercised and ready to solve new problems.


Example: a PNG decoder
----------------------

Decoding the chunks of a PNG file demonstrates Okio in practice.

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
    <version>1.12.0</version>
</dependency>
```
or Gradle:
```groovy
compile 'com.squareup.okio:okio:1.12.0'
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
 [3]: http://square.github.io/okio/1.x/okio/okio/ByteString.html
 [4]: http://square.github.io/okio/1.x/okio/okio/Buffer.html
 [5]: http://square.github.io/okio/1.x/okio/okio/Source.html
 [6]: http://square.github.io/okio/1.x/okio/okio/Sink.html
 [7]: http://square.github.io/okio/1.x/okio/okio/BufferedSource.html
 [8]: http://square.github.io/okio/1.x/okio/okio/BufferedSink.html
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/
