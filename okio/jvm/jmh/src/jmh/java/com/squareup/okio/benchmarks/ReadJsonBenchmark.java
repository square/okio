/*
 * Copyright (C) 2018 Square, Inc. and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.okio.benchmarks;

import com.squareup.moshi.JsonReader;
import okio.Buffer;
import okio.FileSystem;
import okio.Path;
import okio.Source;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.RunnerException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Fork(1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ReadJsonBenchmark {
  private Buffer buffer = null;

  @Setup
  public void setup() throws IOException {
    Source source = FileSystem.SYSTEM.source(Path.get("/Volumes/Development/json-serialization-benchmarking/models/src/main/resources/largesample_minified.json", false));
    buffer = new Buffer();
    buffer.writeAll(source);
    source.close();
  }

  @Benchmark
  public void skipAll() throws IOException {
    JsonReader jsonReader = JsonReader.of(buffer.clone());
    jsonReader.skipValue();
  }

  public static void main(String[] args) throws IOException, RunnerException {
    Main.main(new String[] {ReadJsonBenchmark.class.getName()});
  }
}
