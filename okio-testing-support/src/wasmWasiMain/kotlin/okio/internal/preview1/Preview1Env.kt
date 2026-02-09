// Copyright 2019-2023 the Contributors to the WASI Specification
// This file is adapted from the WASI preview1 spec here:
// https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md

package okio.internal.preview1

/**
 * environ_sizes_get() -> Result<(size, size), errno>
 *
 * Return environment variable data sizes.
 */
@WasmImport("wasi_snapshot_preview1", "environ_sizes_get")
internal external fun environ_sizes_get(
  returnEntryCountPointer: Int,
  returnByteCountPointer: Int,
): Int // should be Short??

/**
 * environ_get(environ: Pointer<Pointer<u8>>, environ_buf: Pointer<u8>) -> Result<(), errno>
 *
 * Read environment variable data.
 * The sizes of the buffers should match that returned by [`environ_sizes_get`](#environ_sizes_get).
 * Key/value pairs are expected to be joined with `=`s, and terminated with `\0`s.
 */
@WasmImport("wasi_snapshot_preview1", "environ_get")
internal external fun environ_get(
  environ: Int,
  environ_buf: Int,
): Int // should be Short??
