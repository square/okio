Okio Benchmarks
------------

This module contains microbenchmarks that can be used to measure various aspects of performance for Okio buffers. Okio benchmarks are written using JMH (version 1.4.1 at this time) and require Java 7.

Running Locally
-------------

To run benchmarks locally, first build and package the project modules:

```
$ mvn clean package
```

This should create a `benchmarks.jar` file in the `target` directory, which is a typical JMH benchmark JAR:

```
$ java -jar benchmarks/target/benchmarks.jar -l
Benchmarks: 
com.squareup.okio.benchmarks.BufferPerformanceBench.cold
com.squareup.okio.benchmarks.BufferPerformanceBench.threads16hot
com.squareup.okio.benchmarks.BufferPerformanceBench.threads1hot
com.squareup.okio.benchmarks.BufferPerformanceBench.threads2hot
com.squareup.okio.benchmarks.BufferPerformanceBench.threads32hot
com.squareup.okio.benchmarks.BufferPerformanceBench.threads4hot
com.squareup.okio.benchmarks.BufferPerformanceBench.threads8hot
```

More help is available using the `-h` option. A typical run on Mac OS X looks like:

```
$ /usr/libexec/java_home -v 1.7 --exec java -jar benchmarks/target/benchmarks.jar \
"cold" -prof gc,hs_rt,stack -r 60 -t 4 \
-jvmArgsPrepend "-Xms1G -Xmx1G -XX:+HeapDumpOnOutOfMemoryError"
```

This executes the "cold" buffer usage benchmark, using the default number of measurement and warm-up iterations, forks, and threads; it adjusts the thread count to 4, iteration time to 60 seconds, fixes the heap size at 1GB and profiles the benchmark using JMH's GC, Hotspot runtime and stack sampling profilers.

