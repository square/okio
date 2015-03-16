Change Log
==========

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
