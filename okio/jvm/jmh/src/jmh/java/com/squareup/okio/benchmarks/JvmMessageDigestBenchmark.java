package com.squareup.okio.benchmarks;

import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

@Fork(1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class JvmMessageDigestBenchmark {

    MessageDigest messageDigest;

    @Param({"100", "1048576"})
    public int messageSize;

    @Param({"SHA-1", "SHA-256", "SHA-512", "MD5"})
    public String algorithm;

    private byte[] message;

    @Setup public void setup() throws NoSuchAlgorithmException {
        messageDigest = MessageDigest.getInstance(algorithm);
        message = new byte[messageSize];
    }

    @Benchmark public void hash() {
        messageDigest.update(message);
        messageDigest.digest();
    }

    public static void main(String[] args) throws IOException {
        Main.main(new String[] { JvmMessageDigestBenchmark.class.getName() });
    }
}
