File System
-----------

Okio 3.0 introduced a multiplatform file system API. These examples work on JVM, native, and
Node.js platforms. In the examples below `fileSystem` is an instance of [FileSystem] such as
`FileSystem.SYSTEM` or `FakeFileSystem`.

Read all of `readme.md` as a string:

```
val path = "readme.md".toPath()
val entireFileString = fileSystem.read(path) {
  readUtf8()
}
```

Read all of `thumbnail.png` as a [ByteString]:

```
val path = "thumbnail.png".toPath()
val entireFileByteString = fileSystem.read(path) {
  readByteString()
}
```

Read all lines of `/etc/hosts` into a `List<String>`:

```
val path = "/etc/hosts".toPath()
val allLines = fileSystem.read(path) {
  generateSequence { readUtf8Line() }.toList()
}
```

Read the prefix of `index.html` that precedes the first `<html>` substring:

```
val path = "index.html".toPath()
val untilHtmlTag = fileSystem.read(path) {
  val htmlTag = indexOf("<html>".encodeUtf8())
  if (htmlTag != -1L) readUtf8(htmlTag) else null
}
```

Write `readme.md` as a string:

```
val path = "readme.md".toPath()
fileSystem.write(path) {
  writeUtf8(
    """
    |Hello, World
    |------------
    |
    |This is a sample file.
    |""".trimMargin()
  )
}
```

Write `data.bin` as a [ByteString]:

```
val path = "data.bin".toPath()
fileSystem.write(path) {
  val byteString = "68656c6c6f20776f726c640a".decodeHex()
  write(byteString)
}
```

Write `readme.md` from a `List<String>`:

```
val path = "readme.md".toPath()
val lines = listOf(
  "Hello, World",
  "------------",
  "",
  "This is a sample file.",
  ""
)
fileSystem.write(path) {
  for (line in lines) {
    writeUtf8(line)
    writeUtf8("\n")
  }
}
```

Generate `binary.txt` programmatically:

```
val path = "binary.txt".toPath()
fileSystem.write(path) {
  for (i in 1 until 100) {
    writeUtf8("$i ${i.toString(2)}")
    writeUtf8("\n")
  }
}
```


[ByteString]: https://square.github.io/okio/2.x/okio/okio/-byte-string/index.html
[FileSystem]: https://square.github.io/okio/2.x/okio/okio/-file-system/index.html
