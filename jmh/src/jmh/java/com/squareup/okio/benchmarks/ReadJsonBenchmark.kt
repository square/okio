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

import com.squareup.cursedmoshi.JsonReader as CursedJsonReader
import com.squareup.moshi.JsonReader as RegularJsonReader
import cursedokio.Buffer as CursedBufffer
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okio.Buffer as RegularBufffer
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
  private val regularJsonFile = File("/Volumes/Development/json-serialization-benchmarking/models/src/main/resources/largesample_minified.json")
  private var regularJson: RegularBufffer = RegularBufffer()
  private var cursedJson: CursedBufffer = CursedBufffer()

  @Setup
  @Throws(IOException::class)
  fun setup() {
    runBlocking {
      val bytes = regularJsonFile.readBytes()
      regularJson.write(bytes)
      cursedJson.write(bytes)
    }
  }

  @Benchmark
  @Throws(IOException::class)
  fun regular() {
    runBlocking {
      val jsonReader = RegularJsonReader.of(regularJson.clone())
      jsonReader.readJsonValue()
    }
  }

  @Benchmark
  @Throws(IOException::class)
  fun cursed() {
    runBlocking {
      val jsonReader = CursedJsonReader.of(cursedJson.clone())
      jsonReader.readJsonValue()
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
