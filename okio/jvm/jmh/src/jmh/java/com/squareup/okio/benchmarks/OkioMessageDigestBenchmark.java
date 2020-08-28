package com.squareup.okio.benchmarks;


import okio.internal.OkioMessageDigest;
import okio.internal.OkioMessageDigestKt;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark                        (algorithm)  (messageSize)  Mode  Cnt      Score     Error  Units
 * OkioMessageDigestBenchmark.hash        SHA-1            100  avgt    5      7.309 ±   0.129  us/op
 * OkioMessageDigestBenchmark.hash        SHA-1        1048576  avgt    5  35893.400 ± 342.705  us/op
 * OkioMessageDigestBenchmark.hash      SHA-256            100  avgt    5      4.156 ±   0.052  us/op
 * OkioMessageDigestBenchmark.hash      SHA-256        1048576  avgt    5  20524.018 ± 209.808  us/op
 * OkioMessageDigestBenchmark.hash      SHA-512            100  avgt    5      4.643 ±   0.034  us/op
 * OkioMessageDigestBenchmark.hash      SHA-512        1048576  avgt    5  19095.547 ± 176.704  us/op
 * OkioMessageDigestBenchmark.hash          MD5            100  avgt    5      5.491 ±   0.117  us/op
 * OkioMessageDigestBenchmark.hash          MD5        1048576  avgt    5  27096.753 ± 407.171  us/op
 *
 * as compared to the JVM 14 default MessageDigest implementation
 *
 * Benchmark                       (algorithm)  (messageSize)  Mode  Cnt     Score     Error  Units
 * JvmMessageDigestBenchmark.hash        SHA-1            100  avgt    5     0.498 ±   0.008  us/op
 * JvmMessageDigestBenchmark.hash        SHA-1        1048576  avgt    5  3622.972 ±  15.425  us/op
 * JvmMessageDigestBenchmark.hash      SHA-256            100  avgt    5     0.431 ±   0.007  us/op
 * JvmMessageDigestBenchmark.hash      SHA-256        1048576  avgt    5  2512.378 ±  12.493  us/op
 * JvmMessageDigestBenchmark.hash      SHA-512            100  avgt    5     0.304 ±   0.006  us/op
 * JvmMessageDigestBenchmark.hash      SHA-512        1048576  avgt    5  1765.034 ± 154.337  us/op
 * JvmMessageDigestBenchmark.hash          MD5            100  avgt    5     0.356 ±   0.018  us/op
 * JvmMessageDigestBenchmark.hash          MD5        1048576  avgt    5  2498.630 ± 144.882  us/op
 *
 */
@Fork(1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class OkioMessageDigestBenchmark {

    OkioMessageDigest messageDigest;

    @Param({"100", "1048576"})
    public int messageSize;

    @Param({"SHA-1", "SHA-256", "SHA-512", "MD5"})
    public String algorithm;

    private byte[] message;

    @Setup public void setup() {
        messageDigest = OkioMessageDigestKt.newMessageDigest(algorithm);
        message = new byte[messageSize];
    }

    @Benchmark public void hash() {
        messageDigest.update(message);
        messageDigest.digest();
    }

    public static void main(String[] args) throws IOException {
        Main.main(new String[] { OkioMessageDigestBenchmark.class.getName() });
    }
}
