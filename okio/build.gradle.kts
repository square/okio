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
 *       |   '-- linux
 *       '-- windows
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
    createNativePlatforms()
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
      val nativeMain by creating {
        dependsOn(nonJvmMain)
        mainSourceSets(nativePlatforms).dependsOn(this)
      }
      val nativeTest by creating {
        dependsOn(commonTest)
        testSourceSets(nativePlatforms).dependsOn(this)
      }
      val unixMain by creating {
        dependsOn(nativeMain)
        mainSourceSets(applePlatforms).dependsOn(this)
        mainSourceSets(linuxPlatforms).dependsOn(this)
      }
      val sizet32Main by creating {
        dependsOn(nativeMain)
        mainSourceSets(sizet32Platforms).dependsOn(this)
      }
      val sizet64Main by creating {
        dependsOn(nativeMain)
        mainSourceSets(sizet64Platforms).dependsOn(this)
      }
      val appleMain by creating {
        mainSourceSets(applePlatforms).dependsOn(this)
      }
      val appleTest by creating {
        dependsOn(nativeTest)
        testSourceSets(applePlatforms).dependsOn(this)
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

val signature by configurations.getting {
}

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

// Without this the build fails on a duplicate of META-INF/proguard/okio.pro. (Not duplicated?!)
tasks.withType<Copy>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.WARN
}
