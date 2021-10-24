Recipes
=======

We've written some recipes that demonstrate how to solve common problems with Okio. Read through
them to learn about how everything works together. Cut-and-paste these examples freely; that's what
they're for.

These recipes work on all platforms: Java, Android, Kotlin/Native, and Kotlin/JS. See
[java.io Recipes](java_io_recipes.md) for samples that integrate Java APIs.


Read a text file line-by-line ([Java][ReadFileLineByLine]/[Kotlin][ReadFileLineByLineKt])
-----------------------------------------------------------------------------------------

Use `FileSystem.source(Path)` to open a source stream to read a file. The returned `Source`
interface is very small and has limited uses. Instead we wrap the source with a buffer. This has two
benefits:

 * **It makes the API more powerful.** Instead of the basic methods offered by `Source`,
   `BufferedSource` has dozens of methods to address most common problems concisely.

 * **It makes your program run faster.** Buffering allows Okio to get more done with fewer I/O
   operations.

Each `Source` that is opened needs to be closed. The code that opens the stream is responsible for
making sure it is closed.

=== "Java"

    Here we use Java's `try` blocks to close our sources automatically.

    ```java
    public void readLines(Path path) throws IOException {
      try (Source fileSource = FileSystem.SYSTEM.source(path);
           BufferedSource bufferedFileSource = Okio.buffer(fileSource)) {

        while (true) {
          String line = bufferedFileSource.readUtf8Line();
          if (line == null) break;

          if (line.contains("square")) {
            System.out.println(line);
          }
        }

      }
    }
    ```

=== "Kotlin"

    This uses `use` to automatically close the streams. This prevents resource leaks, even if an
    exception is thrown.

    ```kotlin
    fun readLines(path: Path) {
      FileSystem.SYSTEM.source(path).use { fileSource ->
        fileSource.buffer().use { bufferedFileSource ->
          while (true) {
            val line = bufferedFileSource.readUtf8Line() ?: break
            if ("square" in line) {
              println(line)
            }
          }
        }
      }
    }
    ```


The `readUtf8Line()` API reads all of the data until the next line delimiter – either `\n`, `\r\n`,
or the end of the file. It returns that data as a string, omitting the delimiter at the end. When it
encounters empty lines the method will return an empty string. If there isn’t any more data to read
it will return null.


=== "Java"

    The above Java program can be written more compactly by inlining the `fileSource` variable and
    by using a fancy `for` loop instead of a `while`:

    ```java
    public void readLines(Path path) throws IOException {
      try (BufferedSource source = Okio.buffer(FileSystem.SYSTEM.source(path))) {
        for (String line; (line = source.readUtf8Line()) != null; ) {
          if (line.contains("square")) {
            System.out.println(line);
          }
        }
      }
    }
    ```

=== "Kotlin"

    In Kotlin, we can use the `FileSystem.read()` function which buffers the source before your
    block and closes the source afterwards. In the body of the block, `this` is a `BufferedSource`.

    ```kotlin
    @Throws(IOException::class)
    fun readLines(file: File) {
      FileSystem.SYSTEM.read(path) {
        while (true) {
          val line = readUtf8Line() ?: break
          if ("square" in line) {
            println(line)
          }
        }
      }
    }
    ```

The `readUtf8Line()` method is suitable for parsing most files. For certain use-cases you may also
consider `readUtf8LineStrict()`. It is similar but it requires that each line is terminated by `\n`
or `\r\n`. If it encounters the end of the file before that it will throw an `EOFException`. The
strict variant also permits a byte limit to defend against malformed input.

=== "Java"

    ```java
    public void readLines(Path path) throws IOException {
      try (BufferedSource source = Okio.buffer(FileSystem.SYSTEM.source(path))) {
        while (!source.exhausted()) {
          String line = source.readUtf8LineStrict(1024L);
          if (line.contains("square")) {
            System.out.println(line);
          }
        }
      }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Throws(IOException::class)
    fun readLines(file: File) {
      FileSystem.SYSTEM.read(path) {
        while (!source.exhausted()) {
          val line = source.readUtf8LineStrict(1024)
          if ("square" in line) {
            println(line)
          }
        }
      }
    }
    ```

 [ReadFileLineByLine]: https://github.com/square/okio/blob/master/samples/src/jvmMain/java/okio/samples/ReadFileLineByLine.java
 [ReadFileLineByLineKt]: https://github.com/square/okio/blob/master/samples/src/jvmMain/kotlin/okio/samples/ReadFileLineByLine.kt
