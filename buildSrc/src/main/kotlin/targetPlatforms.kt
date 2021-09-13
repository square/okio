/*
 * Copyright (C) 2021 Square, Inc.
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

import org.gradle.api.NamedDomainObjectCollection
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

fun KotlinMultiplatformExtension.createNativePlatforms() {
  iosArm64()
  iosX64()
  linuxX64() // Required to generate tests tasks: https://youtrack.jetbrains.com/issue/KT-26547
  macosX64()
  mingwX64()
  tvosArm64()
  tvosX64()
  watchosArm32()
  watchosArm64()
  watchosX86()
}

// Note that size_t is 32-bit on all watchOS versions (ie. pointers are always 32-bit).
val sizet32Platforms = listOf(
  "watchosArm32",
  "watchosArm64",
  "watchosX86",
)

val sizet64Platforms = listOf(
  "iosArm64",
  "iosX64",
  "linuxX64",
  "macosX64",
  "tvosArm64",
  "tvosX64",
)

val applePlatforms = listOf(
  "iosArm64",
  "iosX64",
  "macosX64",
  "tvosArm64",
  "tvosX64",
  "watchosArm32",
  "watchosArm64",
  "watchosX86",
)

val linuxPlatforms = listOf(
  "linuxX64",
)

val windowsPlatforms = listOf(
  "mingwX64",
)

val nativePlatforms = applePlatforms + linuxPlatforms + windowsPlatforms

fun NamedDomainObjectCollection<KotlinSourceSet>.mainSourceSets(
  platforms: List<String>
): List<KotlinSourceSet> {
  return platforms.map { get("${it}Main") }
}

fun NamedDomainObjectCollection<KotlinSourceSet>.testSourceSets(
  platforms: List<String>
): List<KotlinSourceSet> {
  return platforms.map { get("${it}Test") }
}

fun List<KotlinSourceSet>.dependsOn(other: KotlinSourceSet) {
  for (kotlinSourceSet in this) {
    kotlinSourceSet.dependsOn(other)
  }
}
