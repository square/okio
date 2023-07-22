// Copyright 2019-2023 the Contributors to the WASI Specification
// This file is adapted from the WASI preview1 spec here:
// https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md
package okio.internal.preview1

import kotlin.wasm.WasmImport

/** `u32`. */
typealias size = Int

/**
 * `Handle`.
 *
 * A file descriptor handle.
 */
typealias fd = Int

/**
 * `u64`.
 *
 * A reference to the offset of a directory entry.
 *
 * The value 0 signifies the start of the directory.
 */
typealias dircookie = Long

/**
 * `Variant`.
 *
 * Error codes returned by functions. Not all of these error codes are returned by the functions
 * provided by this API; some are used in higher-level library layers, and others are provided
 * merely for alignment with POSIX.
 */
typealias errno = Short

/**
 * `Variant`.
 *
 * The type of a file descriptor or file.
 */
typealias filetype = Byte

/**
 * `u64`.
 *
 * File serial number that is unique within its file system.
 */
typealias inode = Long

/**
 * `u32`.
 *
 * The type for the [`dirent::d_namlen`](#dirent.d_namlen) field of [`dirent`](#dirent) struct.
 */
typealias dirnamelen = Int

/**
 * `Pointer<u8>`.
 */
typealias PointerU8 = Int

/**
 * `lookupflags: Record`.
 *
 * Flags determining the method of how paths are resolved.
 *
 * Bit0:
 * symlink_follow: As long as the resolved path corresponds to a symbolic link, it is expanded.
 */
typealias lookupflags = Int

/**
 * `fdflags: Record`.
 *
 * File descriptor flags.
 *
 * bit0: append: Data written to the file is always appended to the file's end.
 * bit1: dsync: Write according to synchronized I/O data integrity completion. Only the data stored in the file is synchronized.
 * bit2: nonblock: Non-blocking mode.
 * bit3: rsync: bool Synchronized read I/O operations.
 * bit4: sync: bool Write according to synchronized I/O file integrity completion. In addition to synchronizing the data stored in the file, the implementation may also synchronously update the file's metadata.
 */
typealias fdflags = Short

val Stdin: fd = 0
val Stdout: fd = 1
val Stderr: fd = 2

/**
 * Assume the /tmp directory is fd 3.
 *
 * TODO: look this up at runtime from whatever parent directory is requested.
 */
val FirstPreopenDirectoryTmp: fd = 3

/**
 * path_create_directory(fd: fd, path: string) -> Result<(), errno>
 *
 * Create a directory.
 * Note: This is similar to `mkdirat` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "path_create_directory")
internal external fun path_create_directory(
  fd: fd,
  path: PointerU8,
  pathSize: size,
): Int // should be Short??

/**
 * path_open(fd: fd, dirflags: lookupflags, path: string, oflags: oflags, fs_rights_base: rights, fs_rights_inheriting: rights, fdflags: fdflags) -> Result<fd, errno>
 *
 * Open a file or directory.
 * The returned file descriptor is not guaranteed to be the lowest-numbered
 * file descriptor not currently open; it is randomized to prevent
 * applications from depending on making assumptions about indexes, since this
 * is error-prone in multi-threaded contexts. The returned file descriptor is
 * guaranteed to be less than 2**31.
 * Note: This is similar to `openat` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "path_open")
internal external fun path_open(
  fd: fd,
  dirflags: lookupflags,
  path: PointerU8,
  pathSize: size,
  oflags: oflags,
  fs_rights_base: rights,
  fs_rights_inheriting: rights,
  fdflags: fdflags,
  returnPointer: PointerU8,
): Int // should be Short??

/**
 * fd_close(fd: fd) -> Result<(), errno>
 *
 * Close a file descriptor.
 * Note: This is similar to `close` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "fd_close")
internal external fun fd_close(
  fd: fd,
): Int // should be Short??

/**
 * fd_read(fd: fd, iovs: iovec_array) -> Result<size, errno>
 *
 * Read from a file descriptor.
 * Note: This is similar to `readv` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "fd_read")
internal external fun fd_read(
  fd: fd,
  iovs: PointerU8,
  iovsSize: size,
  returnPointer: PointerU8,
): Int // should be Short??

/**
 * fd_readdir(fd: fd, buf: Pointer<u8>, buf_len: size, cookie: dircookie) -> Result<size, errno>
 *
 * Read directory entries from a directory.
 * When successful, the contents of the output buffer consist of a sequence of
 * directory entries. Each directory entry consists of a [`dirent`](#dirent) object,
 * followed by [`dirent::d_namlen`](#dirent.d_namlen) bytes holding the name of the directory
 * entry.
 * This function fills the output buffer as much as possible, potentially
 * truncating the last directory entry. This allows the caller to grow its
 * read buffer size in case it's too small to fit a single large directory
 * entry, or skip the oversized directory entry.
 */
@WasmImport("wasi_snapshot_preview1", "fd_readdir")
internal external fun fd_readdir(
  fd: fd,
  buf: PointerU8,
  buf_len: size,
  cookie: dircookie,
  returnPointer: PointerU8,
): Int // should be Short??

/**
 * fd_write(fd: fd, iovs: ciovec_array) -> Result<size, errno>
 *
 * Write to a file descriptor.
 * Note: This is similar to `writev` in POSIX.
 *
 * Like POSIX, any calls of `write` (and other functions to read or write)
 * for a regular file by other threads in the WASI process should not be
 * interleaved while `write` is executed.
 */
@WasmImport("wasi_snapshot_preview1", "fd_write")
internal external fun fd_write(
  fd: fd,
  iovs: PointerU8,
  iovsSize: size,
  returnPointer: PointerU8,
): Int // should be Short??
