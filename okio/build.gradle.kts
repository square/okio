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
 * Every child of `unix` also includes a source set that depends on the pointer size:
 *
 *  * `sizet32` for watchOS, including watchOS 64-bit architectures
 *  * `sizet64` for everything else
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
    all {
      languageSettings.apply {
        useExperimentalAnnotation("kotlin.RequiresOptIn")
      }
    }

    val commonMain by getting {
      dependencies {
        api(deps.kotlin.stdLib.common)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(deps.kotlin.test.common)
        implementation(deps.kotlin.test.annotations)
        implementation(deps.kotlin.time)

        implementation(project(":okio-fakefilesystem"))
        implementation(project(":okio-testing-support"))
      }
    }
    val nonJvmMain by creating {
      kotlin.srcDir("src/hashFunctions/kotlin")
    }

    val jvmMain by getting {
      dependencies {
        api(deps.kotlin.stdLib.jdk8)
        compileOnly(deps.animalSniffer.annotations)
      }
    }
    val jvmTest by getting {
      kotlin.srcDir("src/hashFunctions/kotlin")
      dependencies {
        implementation(deps.test.junit)
        implementation(deps.test.assertj)
        implementation(deps.kotlin.test.jdk)
      }
    }

    if (kmpJsEnabled) {
      val jsMain by getting {
        dependsOn(nonJvmMain)
        dependencies {
          api(deps.kotlin.stdLib.js)
        }
      }
      val jsTest by getting {
        dependencies {
          implementation(deps.kotlin.test.js)
        }
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
              createSourceSet("sizet32Main", parent = unixMain, children = unixSizet32Targets)
              createSourceSet("sizet64Main", parent = unixMain, children = unixSizet64Targets)
            }
        }

      createSourceSet("nativeTest", parent = commonTest, children = mingwTargets + linuxTargets)
        .also { nativeTest ->
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

// https://github.com/vanniktech/gradle-maven-publish-plugin/issues/301
val metadataJar by tasks.getting(Jar::class)
configure<PublishingExtension> {
  publications.withType<MavenPublication>().named("kotlinMultiplatform").configure {
    artifact(metadataJar)
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinMultiplatform(javadocJar = Dokka("dokkaGfm"))
  )
}

// https://youtrack.jetbrains.com/issue/KT-46978
tasks.withType<ProcessResources>().all {
  when (name) {
    "jvmTestProcessResources", "jvmProcessResources" -> {
      duplicatesStrategy = DuplicatesStrategy.WARN
    }
  }
}
