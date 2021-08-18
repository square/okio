import aQute.bnd.gradle.BundleTaskConvention
import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension

plugins {
  kotlin("multiplatform")
  id("ru.vyarus.animalsniffer")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("build-support")
}

/*
 * Here's the main hierarchy of variants. Any `expect` functions in one level of the tree are
 * `actual` functions in a (potentially indirect) child node.
 *
 * ```
 *   common
 *   |-- jvm
 *   |-- js
 *   '-- native
 *       |- unix
 *       |   |-- apple
 *       |   |   |-- iosArm64
 *       |   |   |-- iosX64
 *       |   |   |-- macosX64
 *       |   |   |-- tvosArm64
 *       |   |   |-- tvosX64
 *       |   |   |-- watchosArm32
 *       |   |   |-- watchosArm64
 *       |   |   '-- watchosX86
 *       |   '-- linux
 *       |       '-- linuxX64
 *       '-- mingw
 *           '-- mingwX64
 * ```
 *
 * The `nonJvm` source set excludes that platform.
 *
 * The `hashFunctions` source set builds on all platforms. It ships as a main source set on non-JVM
 * platforms and as a test source set on the JVM platform.
 */
kotlin {
  jvm {
    withJava()
  }
  if (kmpJsEnabled) {
    js {
      compilations.all {
        kotlinOptions {
          moduleKind = "umd"
          sourceMap = true
          metaInfo = true
        }
      }
      nodejs {
        testTask {
          useMocha {
            timeout = "30s"
          }
        }
      }
      browser {
      }
    }
  }
  if (kmpNativeEnabled) {
    configureOrCreateNativePlatforms()
  }
  sourceSets {
    val commonMain by getting
    val commonTest by getting {
      dependencies {
        implementation(deps.kotlin.test)
        implementation(deps.kotlin.time)

        implementation(project(":okio-fakefilesystem"))
        implementation(project(":okio-testing-support"))
      }
    }

    val hashFunctions by creating {
      dependsOn(commonMain)
    }

    val nonJvmMain by creating {
      dependsOn(hashFunctions)
      dependsOn(commonMain)
    }
    val nonJvmTest by creating {
      dependsOn(commonTest)
    }

    val jvmMain by getting {
      dependencies {
        compileOnly(deps.animalSniffer.annotations)
      }
    }
    val jvmTest by getting {
      kotlin.srcDir("src/jvmTest/hashFunctions")
      dependencies {
        implementation(deps.test.junit)
        implementation(deps.test.assertj)
      }
    }

    if (kmpJsEnabled) {
      val jsMain by getting {
        dependsOn(nonJvmMain)
      }
      val jsTest by getting {
        dependsOn(nonJvmTest)
      }
    }

    if (kmpNativeEnabled) {
      createSourceSet("nativeMain", parent = nonJvmMain)
        .also { nativeMain ->
          createSourceSet("mingwMain", parent = nativeMain, children = mingwTargets)
          createSourceSet("unixMain", parent = nativeMain)
            .also { unixMain ->
              createSourceSet("linuxMain", parent = unixMain, children = linuxTargets)
              createSourceSet("appleMain", parent = unixMain, children = appleTargets)
            }
        }

      createSourceSet("nativeTest", parent = commonTest, children = mingwTargets + linuxTargets)
        .also { nativeTest ->
          nativeTest.dependsOn(nonJvmTest)
          createSourceSet("appleTest", parent = nativeTest, children = appleTargets)
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
    val bndConvention = BundleTaskConvention(this)
    bndConvention.setBnd(
      """
      Export-Package: okio
      Automatic-Module-Name: okio
      Bundle-SymbolicName: com.squareup.okio
      """
    )
    // Call the convention when the task has finished to modify the jar to contain OSGi metadata.
    doLast {
      bndConvention.buildBundle()
    }
  }
}

configure<AnimalSnifferExtension> {
  sourceSets = listOf(project.sourceSets.getByName("main"))
}

val signature: Configuration by configurations

dependencies {
  signature(deps.animalSniffer.androidSignature)
  signature(deps.animalSniffer.javaSignature)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinMultiplatform(javadocJar = Dokka("dokkaGfm"))
  )
}
