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
package com.squareup.okio.benchmarks

import com.squareup.cursedmoshi.JsonReader.Companion.of
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import org.openjdk.jmh.Main
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.runner.RunnerException

@Fork(1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
open class ReadJsonBenchmark {
  private val jsonFile = "/Volumes/Development/json-serialization-benchmarking/models/src/main/resources/largesample_minified.json".toPath()
  private var json: Buffer = Buffer()

  @Setup
  @Throws(IOException::class)
  fun setup() {
    FileSystem.SYSTEM.read(jsonFile) {
      json.writeAll(this)
    }
  }

  @Benchmark
  @Throws(IOException::class)
  fun skipAll() {
    runBlocking {
      val jsonReader = of(json.clone())
      jsonReader.skipValue()
    }
  }

  companion object {
    @Throws(IOException::class, RunnerException::class)
    @JvmStatic
    fun main(args: Array<String>) {
      Main.main(arrayOf<String>(ReadJsonBenchmark::class.java.getName()))
    }
  }
}
