// Copyright 2019-2023 the Contributors to the WASI Specification
// This file is adapted from the WASI preview1 spec here:
// https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md
package okio.internal.preview1

import kotlin.wasm.WasmImport

/**
 * clock_time_get(id: clockid, precision: timestamp) -> Result<timestamp, errno>
 *
 * Return the time value of a clock.
 * Note: This is similar to `clock_gettime` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "clock_time_get")
internal external fun clock_time_get(
  id: clockid,
  precision: Long,
  returnPointer: Int,
): Int // should be Short??
