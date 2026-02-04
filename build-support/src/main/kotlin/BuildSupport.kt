/*
 * Copyright (C) 2023 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.nio.charset.StandardCharsets
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJsProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

class BuildSupport : Plugin<Project> {
  override fun apply(project: Project) {
    project.configureCommonKotlin()
  }

  private fun Project.configureCommonKotlin() {
    tasks.withType(KotlinCompilationTask::class.java).configureEach {
      compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
      }
    }

    extensions.configure(KotlinProjectExtension::class.java) {
      coreLibrariesVersion = project.getVersionByName("kotlinCoreLibrariesVersion")
      jvmToolchain(24)
    }

    val javaVersion = JavaVersion.VERSION_1_8
    tasks.withType(KotlinJvmCompile::class.java).configureEach {
      compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
        freeCompilerArgs.add("-Xjvm-default=all")
      }
    }
    // Kotlin requires the Java compatibility matches.
    tasks.withType(JavaCompile::class.java).configureEach {
      options.encoding = StandardCharsets.UTF_8.toString()
      sourceCompatibility = javaVersion.toString()
      targetCompatibility = javaVersion.toString()
    }

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
      val kotlin = extensions.getByName("kotlin") as KotlinMultiplatformExtension
      kotlin.configureWebKotlinLibraries()
    }
    plugins.withId("org.jetbrains.kotlin.js") {
      val kotlin = extensions.getByName("kotlin") as KotlinJsProjectExtension
      kotlin.configureWebKotlinLibraries()
    }
  }

  // For KotlinWasm/Js, versions of toolchain and stdlib need to be the same:
  // https://youtrack.jetbrains.com/issue/KT-71032
  private fun KotlinProjectExtension.configureWebKotlinLibraries() {
    val kotlinVersion = project.getVersionByName("kotlin")

    when (this) {
      is KotlinJsProjectExtension -> {
        val suffix = "js"
        sourceSets.apply {
          getByName("main").dependencies {
            implementation("org.jetbrains.kotlin:kotlin-stdlib-$suffix:$kotlinVersion")
          }
          getByName("test").dependencies {
            implementation("org.jetbrains.kotlin:kotlin-stdlib-$suffix:$kotlinVersion")
            implementation("org.jetbrains.kotlin:kotlin-test-$suffix:$kotlinVersion")
          }
        }
      }

      is KotlinMultiplatformExtension -> {
        targets.matching { it.platformType in listOf(KotlinPlatformType.js, KotlinPlatformType.wasm) }.configureEach {
          val suffix = when (platformType) {
            KotlinPlatformType.js -> "js"
            KotlinPlatformType.wasm -> if (targetName.contains("wasi", true)) "wasm-wasi" else "wasm-js"
            else -> return@configureEach
          }

          this@configureWebKotlinLibraries.sourceSets.apply {
            getByName("${targetName}Main").dependencies {
              implementation("org.jetbrains.kotlin:kotlin-stdlib-$suffix:$kotlinVersion")
            }

            getByName("${targetName}Test").dependencies {
              implementation("org.jetbrains.kotlin:kotlin-stdlib-$suffix:$kotlinVersion")
              implementation("org.jetbrains.kotlin:kotlin-test-$suffix:$kotlinVersion")
            }
          }
        }
      }
    }
  }
}

val Project.versionCatalog: VersionCatalog
  get() = project.extensions.getByType(VersionCatalogsExtension::class.java).find("libs").get()

fun Project.getVersionByName(name: String): String {
  val version = versionCatalog.findVersion(name)
  return if (version.isPresent) {
    version.get().requiredVersion
  } else {
    throw GradleException("Could not find a version for `$name`")
  }
}
