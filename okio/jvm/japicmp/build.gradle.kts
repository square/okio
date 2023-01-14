/*
 * Copyright (C) 2019 Square, Inc.
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
import me.champeau.gradle.japicmp.JapicmpTask

plugins {
  `java-library`
  id("me.champeau.gradle.japicmp")
}

val baseline: Configuration = configurations.create("baseline")
val latest: Configuration = configurations.create("latest")

dependencies {
  baseline("com.squareup.okio:okio:1.17.5") {
    isTransitive = false
    isForce = true
  }
  latest(projects.okio) { targetConfiguration = "jvmRuntimeElements" }
}

val japicmp = tasks.register<JapicmpTask>("japicmp") {
  dependsOn(tasks.jar)
  oldClasspath.setFrom(baseline)
  newClasspath.setFrom(latest)
  onlyBinaryIncompatibleModified.set(true)
  failOnModification.set(true)
  txtOutputFile.set(file("$buildDir/reports/japi.txt"))
  ignoreMissingClasses.set(true)
  includeSynthetic.set(true)
  classExcludes.set(listOf(
    "okio.ByteString", // Bytecode version changed from 51.0 to 50.0
    "okio.RealBufferedSink", // Internal.
    "okio.RealBufferedSource", // Internal.
    "okio.SegmentedByteString", // Internal.
    "okio.SegmentPool", // Internal.
    "okio.Util", // Internal.
    "okio.Options" // Bytecode version changed from 51.0 to 50.0
  ))
  methodExcludes.set(listOf(
    "okio.ByteString#getByte(int)", // Became "final" in 1.15.0.
    "okio.ByteString#size()" // Became "final" in 1.15.0.
  ))
}

tasks.check.configure {
  dependsOn(japicmp)
}
