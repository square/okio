// Copyright 2019-2023 the Contributors to the WASI Specification
// This file is adapted from the WASI preview1 spec here:
// https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md
package okio.internal.preview1

internal typealias clockid = Int

/**
 * The clock measuring real time. Time value zero corresponds with
 * 1970-01-01T00:00:00Z.
 */
internal val clockid_realtime = 0

/**
 * The store-wide monotonic clock, which is defined as a clock measuring
 * real time, whose value cannot be adjusted and which cannot have negative
 * clock jumps. The epoch of this clock is undefined. The absolute time
 * value of this clock therefore has no meaning.
 */
internal val clockid_monotonic = 1

/**
 * The CPU-time clock associated with the current process.
 */
internal val clockid_process_cputime_id = 2

/**
 * The CPU-time clock associated with the current thread.
 */
internal val clockid_thread_cputime_id = 3
