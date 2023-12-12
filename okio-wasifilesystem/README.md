WASI FileSystem
===============

⚠️ This is a work in progress ⚠️

This module implements Okio's FileSystem API using the [WebAssembly System Interface (WASI)][wasi].

It currently uses the WASI [preview1] APIs and is tested on NodeJS with the
`--experimental-wasi-unstable-preview1` option.

[wasi]: https://wasi.dev/
[preview1]: https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md
