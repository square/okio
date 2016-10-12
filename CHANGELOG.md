Change Log
==========

## Version 1.11.0

_2016-10-11_

 * **Fix: The four-argument overload of `Buffer.writeString()` had a major bug
   where it didn't respect offsets if the specified charset was UTF-8.** This
   was because our short-circuit optimization omitted necessary offset
   parameters.
 * New: HMAC support in `HashingSource`, `HashingSink`, `ByteString`, and
   `Buffer`. This makes it easy to create a keyed-hash message authentication
   code (HMAC) wherever your data is. Unlike the other hashes, HMAC uses a
   `ByteString` secret key for authentication.
 * New: `ByteString.of(ByteBuffer)` makes it easier to mix NIO with Okio.


## Version 1.10.0

_2016-08-28_

 * Fix: Support reading files larger than 2 GiB with `GzipSource`. Previously
   attempting to decompress such files would fail due to an overflow when
   validating the total length.
 * Fix: Exit the watchdog thread after being idle for 60 seconds. This should
   make it possible for class unloaders to fully unload Okio.
 * New: `Okio.blackhole()` returns a sink where all bytes written are discarded.
   This is Okio's equivalent of `/dev/null`.
 * New: Encode a string with any charset using `ByteString.encodeString()` and
   decode strings in any charset using `ByteString.string()`. Most applications
   should prefer `ByteString.encodeUtf8()` and `ByteString.utf8()` unless it's
   necessary to support a legacy charset.
 * New: `GzipSink.deflater()` makes it possible to configure the compression
   level.


## Version 1.9.0

_2016-07-01_

 * New: `Pipe` makes it easy to connect a producer thread to a consumer thread.
   Reads block until data is available to read. Writes block if the pipe's is
   full. Both sources and sinks support timeouts.
 * New: `BufferedSource.rangeEquals()` makes it easy to compare a range in a
   stream to an expected value. This does the right thing: it blocks to load
   the data required return a definitive result. But it won't block
   unnecessarily.
 * New: `Timeout.waitUntilNotified()` makes it possible to use nice timeout
   abstractions on Java's built-in wait/notify primitives.
 * Fix: Don't return incorrect results when `HashingSource` does large reads.
   There was a bug where it wasn't traversing through the segments of the buffer
   being hashed. This means that `HashingSource` was returning incorrect answers
   for any writes that spanned multiple segment boundaries.

## Version 1.8.0

_2016-05-02_

 * New: `BufferedSource.select(Options)` API for reading one of a set of
   expected values.
 * New: Make `ByteString.toString()` and `Buffer.toString()` friendlier.
   These methods return text if the byte string is valid UTF-8.
 * New: APIs to match byte strings: `indexOf()`, `startsWith()`, and
   `endsWith()`.

## Version 1.7.0

_2016-04-10_

 * New: Change the segment size to 8 KiB. This has been reported to dramatically
   improve performance in some applications.
 * New: `md5()`, `sha1()`, and `sha256()` methods on `Buffer`. Also add a
   `sha1()` method on `ByteString` for symmetry.
 * New: `HashingSource` and `HashingSink`. These classes are Okio’s equivalent
   to the JDK’s `DigestInputStream` and `DigestOutputStream`. They offer
   convenient `md5()`, `sha1()`, and `sha256()` factory methods to avoid an
   impossible `NoSuchAlgorithmException`.
 * New: `ByteString.asByteBuffer()`.
 * Fix: Limit snapshot byte strings to requested size.
 * Fix: Change write timeouts to have a maximum write size. Previously large
   writes could easly suffer timeouts because the entire write was subject to a
   single timeout.
 * Fix: Recover from EBADF failures, which could be triggered by asynchronously
   closing a stream on older versions of Android.
 * Fix: Don't share segments if doing so only saves a small copy. This should
   improve performance for all applications.
 * Fix: Optimize `BufferedSource.indexOfElement()` and `indexOf(ByteString)`.
   Previously this method had a bug that caused it to be very slow on large
   buffers.

## Version 1.6.0

_2015-08-25_

 * New: `BufferedSource.indexOf(ByteString)` searches a source for the next
   occurrence of a byte string.
 * Fix: Recover from unexpected `AssertionError` thrown on Android 4.2.2 and
   earlier when asynchronously closing a socket.

## Version 1.5.0

_2015-06-19_

 * Sockets streams now throw `SocketTimeoutException`. This builds on new
   extension point in `AsyncTimeout` to customize the exception when a timeout
   occurs.
 * New: `ByteString` now implements `Comparable`. The comparison sorts bytes as
   unsigned: {@code ff} sorts after {@code 00}.

## Version 1.4.0

_2015-05-16_

 * **Timeout exception changed.** Previously `Timeout.throwIfReached()` would
   throw `InterruptedIOException` on thread interruption, and `IOException` if
   the deadline was reached. Now it throws `InterruptedIOException` in both
   cases.
 * Fix: throw `EOFException` when attempting to read digits from an empty
   source. Previously this would crash with an unchecked exception.
 * New: APIs to read and write UTF-8 code points without allocating strings.
 * New: `BufferedSink` can now write substrings directly, potentially saving an
   allocation for some callers.
 * New: `ForwardingTimeout` class.

## Version 1.3.0

_2015-03-16_

 * New: Read and write signed decimal and unsigned hexadecimal values in
   `BufferedSource` and `BufferedSink`. Unlike the alternatives, these methods
   don’t do any memory allocations!
 * New: Segment sharing. This improves the runtime of operations like
   `Buffer.clone()` and `Buffer.copyTo()` by sharing underlying segments between
   buffers.
 * New: `Buffer.snapshot()` returns an immutable snapshot of a buffer as a
   `ByteString`. This builds on segment sharing so that snapshots are shallow,
   immutable copies.
 * New: `ByteString.rangeEquals()`.
 * New: `ByteString.md5()` and `ByteString.sha256()`.
 * New: `ByteString.base64Url()` returns URL-safe Base64. The existing
   decoding method has been extended to support URL-safe Base64 input.
 * New: `ByteString.substring()` returns a prefix, infix, or suffix.
 * New: `Sink` now implements `java.io.Flushable`.
 * Fix: `Buffer.write(Source, long)` now always writes fully. The previous
   behavior would return as soon as any data had been written; this was
   inconsistent with all other _write()_ methods in the API.
 * Fix: don't leak empty segments in DeflaterSink and InflaterSource. (This was
   unlikely to cause problems in practice.)

## Version 1.2.0

_2014-12-30_

 * Fix: `Okio.buffer()` _always_ buffers for better predictability.
 * Fix: Provide context when `readUtf8LineStrict()` throws.
 * Fix: Buffers do not call through the `Source` on zero-byte writes.

## Version 1.1.0

_2014-12-11_

 * Do UTF-8 encoding natively for a performance increase, particularly on Android.
 * New APIs: `BufferedSink.emit()`, `BufferedSource.request()` and `BufferedSink.indexOfElement()`.
 * Fixed a performance bug in `Buffer.indexOf()`

## Version 1.0.1

_2014-08-08_

 * Added `read(byte[])`, `read(byte[], offset, byteCount)`,  and
   `void readFully(byte[])` to `BufferedSource`.
 * Refined declared checked exceptions on `Buffer` methods.


## Version 1.0.0

_2014-05-23_

 * Bumped release version. No other changes!

## Version 0.9.0

_2014-05-03_

 * Use 0 as a sentinel for no timeout.
 * Make AsyncTimeout public.
 * Remove checked exception from Buffer.readByteArray.

## Version 0.8.0

_2014-04-24_

 * Eagerly verify preconditions on public APIs.
 * Quick return on Buffer instance equivalence.
 * Add delegate types for Sink and Source.
 * Small changes to the way deadlines are managed.
 * Add append variant of Okio.sink for File.
 * Methods to exhaust BufferedSource to byte[] and ByteString.

## Version 0.7.0

_2014-04-18_

 * Don't use getters in timeout.
 * Use the watchdog to interrupt sockets that have reached deadlines.
 * Add java.io and java.nio file source/sink helpers.

## Version 0.6.1

_2014-04-17_

 * Methods to read a buffered source fully in UTF-8 or supplied charset.
 * API to read a byte[] directly.
 * New methods to move all data from a source to a sink.
 * Fix a bug on input stream exhaustion.

## Version 0.6.0

_2014-04-15_

 * Make ByteString serializable.
 * New API: `ByteString.of(byte[] data, int offset, int byteCount)`
 * New API: stream-based copy, write, and read helpers.

## Version 0.5.0

_2014-04-08_

 * Initial public release.
 * Imported from OkHttp.
