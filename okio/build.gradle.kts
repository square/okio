import aQute.bnd.gradle.BundleTaskExtension
import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import kotlinx.validation.ApiValidationExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

plugins {
  kotlin("multiplatform")
  id("app.cash.burst")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("build-support")
  id("binary-compatibility-validator")
}

/*
 * Here's the main hierarchy of variants. Any `expect` functions in one level of the tree are
 * `actual` functions in a (potentially indirect) child node.
 *
 * ```
 *   common
 *   |-- js
 *   |-- jvm
 *   |-- native
 *   |   |-- mingw
 *   |   |   '-- mingwX64
 *   |   '-- unix
 *   |       |-- apple
 *   |       |   |-- iosArm64
 *   |       |   |-- iosX64
 *   |       |   |-- macosX64
 *   |       |   |-- tvosArm64
 *   |       |   |-- tvosX64
 *   |       |   |-- watchosArm32
 *   |       |   |-- watchosArm64
 *   |       '-- linux
 *   |           |-- linuxX64
 *   |           '-- linuxArm64
 *   '-- wasm
 *       '-- wasmJs
 *       '-- wasmWasi
 * ```
 *
 * The `nonJvm` source set excludes that platform.
 *
 * The `hashFunctions` source set builds on all platforms. It ships as a main source set on non-JVM
 * platforms and as a test source set on the JVM platform.
 *
 * The `systemFileSystem` source set is used on jvm and native targets, and provides the FileSystem.SYSTEM property.
 */
kotlin {
  configureOrCreateOkioPlatforms()

  sourceSets {
    all {
      languageSettings.apply {
        // Required for CPointer etc. since Kotlin 1.9.
        optIn("kotlinx.cinterop.ExperimentalForeignApi")
      }
    }

    val commonMain by getting
    val commonTest by getting {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(projects.okioTestingSupport)
      }
    }

    val hashFunctions by creating {
      dependsOn(commonMain)
    }

    val nonAppleMain by creating {
      dependsOn(hashFunctions)
    }

    val nonWasmTest by creating {
      dependsOn(commonTest)
      dependencies {
        implementation(libs.kotlin.time)
        implementation(projects.okioFakefilesystem)
      }
    }

    val nonJvmMain by creating {
      dependsOn(hashFunctions)
      dependsOn(commonMain)
    }

    val systemFileSystemMain by creating {
      dependsOn(commonMain)
    }

    val nonJvmTest by creating {
      dependsOn(commonTest)
    }

    val zlibMain by creating {
      dependsOn(commonMain)
    }

    val zlibTest by creating {
      dependsOn(commonTest)
      dependencies {
        implementation(libs.test.assertk)
      }
    }

    val jvmMain by getting {
      dependsOn(zlibMain)
      dependsOn(systemFileSystemMain)
    }
    val jvmTest by getting {
      kotlin.srcDir("src/hashFunctions")
      dependsOn(nonWasmTest)
      dependsOn(zlibTest)
      dependencies {
        implementation(libs.test.junit)
        implementation(libs.test.assertj)
        implementation(libs.test.jimfs)
      }
    }

    if (kmpJsEnabled) {
      val jsMain by getting {
        dependsOn(nonJvmMain)
        dependsOn(nonAppleMain)
      }
      val jsTest by getting {
        dependsOn(nonWasmTest)
        dependsOn(nonJvmTest)
      }
    }

    if (kmpNativeEnabled) {
      createSourceSet("nativeMain", parent = nonJvmMain)
        .also { nativeMain ->
          nativeMain.dependsOn(zlibMain)
          nativeMain.dependsOn(systemFileSystemMain)
          createSourceSet("mingwMain", parent = nativeMain, children = mingwTargets).also { mingwMain ->
            mingwMain.dependsOn(nonAppleMain)
          }
          createSourceSet("unixMain", parent = nativeMain)
            .also { unixMain ->
              createSourceSet("linuxMain", parent = unixMain, children = linuxTargets).also { linuxMain ->
                linuxMain.dependsOn(nonAppleMain)
              }
              createSourceSet("appleMain", parent = unixMain, children = appleTargets)
            }
        }

      createSourceSet("nativeTest", parent = commonTest, children = mingwTargets + linuxTargets)
        .also { nativeTest ->
          nativeTest.dependsOn(nonJvmTest)
          nativeTest.dependsOn(nonWasmTest)
          nativeTest.dependsOn(zlibTest)
          createSourceSet("appleTest", parent = nativeTest, children = appleTargets)
        }
    }

    if (kmpWasmEnabled) {
      createSourceSet("wasmMain", parent = commonMain, children = wasmTargets)
        .also { wasmMain ->
          wasmMain.dependsOn(nonJvmMain)
          wasmMain.dependsOn(nonAppleMain)
        }
      createSourceSet("wasmTest", parent = commonTest, children = wasmTargets)
        .also { wasmTest ->
          wasmTest.dependsOn(nonJvmTest)
        }
    }
  }

  targets.withType<KotlinNativeTargetWithTests<*>> {
    binaries {
      // Configure a separate test where code runs in background
      test("background", setOf(NativeBuildType.DEBUG)) {
        freeCompilerArgs += "-trw"
      }
    }
    testRuns {
      val background by creating {
        setExecutionSourceFrom(binaries.getByName("backgroundDebugTest") as TestExecutable)
      }
    }
  }
}

tasks {
  val jvmJar by getting(Jar::class) {
    // BundleTaskExtension() crashes unless there's a 'main' source set.
    sourceSets.create(SourceSet.MAIN_SOURCE_SET_NAME)
    val bndExtension = BundleTaskExtension(this)
    bndExtension.setBnd(
      """
      Export-Package: okio
      Automatic-Module-Name: okio
      Bundle-SymbolicName: com.squareup.okio
      """,
    )
    // Call the extension when the task has finished to modify the jar to contain OSGi metadata.
    doLast {
      bndExtension.buildAction()
        .execute(this)
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinMultiplatform(javadocJar = Dokka("dokkaGfm")),
  )
}

plugins.withId("binary-compatibility-validator") {
  configure<ApiValidationExtension> {
    ignoredProjects += "jmh"
  }
}
