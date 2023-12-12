// Copyright 2019-2023 the Contributors to the WASI Specification
// This file is adapted from the WASI preview1 spec here:
// https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md
package okio.internal.preview1

/**
 * `Variant`.
 *
 * The type of a file descriptor or file.
 */
typealias filetype = Byte

/** The type of the file descriptor or file is unknown or is different from any of the other types specified. */
val filetype_unknown: filetype = 0

/** The file descriptor or file refers to a block device inode. */
val filetype_block_device: filetype = 1

/** The file descriptor or file refers to a character device inode. */
val filetype_character_device: filetype = 2

/** The file descriptor or file refers to a directory inode. */
val filetype_directory: filetype = 3

/** The file descriptor or file refers to a regular file inode. */
val filetype_regular_file: filetype = 4

/** The file descriptor or file refers to a datagram socket. */
val filetype_socket_dgram: filetype = 5

/** The file descriptor or file refers to a byte-stream socket. */
val filetype_socket_stream: filetype = 6

/** The file refers to a symbolic link inode. */
val filetype_symbolic_link: filetype = 7
