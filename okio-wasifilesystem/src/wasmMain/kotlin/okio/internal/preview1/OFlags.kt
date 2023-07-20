// Copyright 2019-2023 the Contributors to the WASI Specification
// This file is adapted from the WASI preview1 spec here:
// https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md
package okio.internal.preview1

/**
 * `oflags: `Record`.
 *
 * Open flags used by path_open.
 */
typealias oflags = Int

/** Create file if it does not exist. */
val oflag_creat = 1 shl 0

/** Fail if not a directory. */
val oflag_directory = 1 shl 1

/** Fail if file already exists. */
val oflag_excl = 1 shl 2

/** Truncate file to size 0. */
val oflag_trunc = 1 shl 3
