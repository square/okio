package com.squareup.okio.benchmarks;

import okio.internal.OkioMessageDigest;
import okio.internal.OkioMessageDigestKt;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

@Fork(1)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MessageDigestBenchmark {

    MessageDigest jvmMessageDigest;
    OkioMessageDigest okioMessageDigest;

    @Param({"100", "1048576"})
    public int messageSize;

    @Param({"SHA-1", "SHA-256", "SHA-512", "MD5"})
    public String algorithm;

    private byte[] message;

    @Setup public void setup() throws NoSuchAlgorithmException {
        jvmMessageDigest = MessageDigest.getInstance(algorithm);
        okioMessageDigest = OkioMessageDigestKt.newMessageDigest(algorithm);
        message = new byte[messageSize];
    }

    @Benchmark public void jvm() {
        jvmMessageDigest.update(message);
        jvmMessageDigest.digest();
    }

    @Benchmark public void okio() {
        okioMessageDigest.update(message);
        okioMessageDigest.digest();
    }

    public static void main(String[] args) throws IOException {
        Main.main(new String[] { MessageDigestBenchmark.class.getName() });
    }
}
