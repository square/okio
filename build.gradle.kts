import aQute.bnd.gradle.BundleTaskExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import groovy.util.Node
import groovy.util.NodeList
import java.nio.charset.StandardCharsets
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("build-support").apply(false)
}

buildscript {
  dependencies {
    classpath(libs.android.gradle.plugin)
    classpath(libs.burst.gradle.plugin)
    classpath(libs.dokka)
    classpath(libs.jmh.gradle.plugin)
    classpath(libs.binaryCompatibilityValidator)
    classpath(libs.spotless)
    classpath(libs.bnd)
    classpath(libs.vanniktech.publish.plugin)
  }

  repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
  }
}

apply(plugin = "com.vanniktech.maven.publish.base")

// When scripts are applied the buildscript classes are not accessible directly therefore we save
// the class here to make it accessible.
ext.set("bndBundleTaskExtensionClass", BundleTaskExtension::class.java)

allprojects {
  group = project.property("GROUP") as String
  version = project.property("VERSION_NAME") as String

  repositories {
    mavenCentral()
    google()
  }

  tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets.configureEach {
      reportUndocumented.set(false)
      skipDeprecated.set(true)
      jdkVersion.set(8)
      perPackageOption {
        matchingRegex.set("com\\.squareup.okio.*")
        suppress.set(true)
      }
      perPackageOption {
        matchingRegex.set("okio\\.internal.*")
        suppress.set(true)
      }
    }

    if (name == "dokkaHtml") {
      outputDirectory.set(file("${rootDir}/docs/3.x/${project.name}"))
      pluginsMapConfiguration.set(
        mapOf(
          "org.jetbrains.dokka.base.DokkaBase" to """
          {
            "customStyleSheets": [
              "${rootDir.toString().replace('\\', '/')}/docs/css/dokka-logo.css"
            ],
            "customAssets" : [
              "${rootDir.toString().replace('\\', '/')}/docs/images/icon-square.png"
            ]
          }
          """.trimIndent()
        )
      )
    }
  }

  plugins.withId("com.vanniktech.maven.publish.base") {
    configure<PublishingExtension> {
      repositories {
        /**
         * Want to push to an internal repository for testing? Set the following properties in
         * `~/.gradle/gradle.properties`.
         *
         * internalMavenUrl=YOUR_INTERNAL_MAVEN_REPOSITORY_URL
         * internalMavenUsername=YOUR_USERNAME
         * internalMavenPassword=YOUR_PASSWORD
         */
        val internalUrl = providers.gradleProperty("internalUrl")
        if (internalUrl.isPresent) {
          maven {
            name = "internal"
            setUrl(internalUrl)
            credentials(PasswordCredentials::class)
          }
        }
      }
    }
    val publishingExtension = extensions.getByType(PublishingExtension::class.java)
    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(SonatypeHost.S01, automaticRelease = true)
      signAllPublications()
      pom {
        description.set("A modern I/O library for Android, Java, and Kotlin Multiplatform.")
        name.set(project.name)
        url.set("https://github.com/square/okio/")
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        scm {
          url.set("https://github.com/square/okio/")
          connection.set("scm:git:git://github.com/square/okio.git")
          developerConnection.set("scm:git:ssh://git@github.com/square/okio.git")
        }
        developers {
          developer {
            id.set("square")
            name.set("Square, Inc.")
          }
        }
      }

      // Configure the kotlinMultiplatform artifact to depend on the JVM artifact in pom.xml only.
      // This hack allows Maven users to continue using our original Okio artifact names (like
      // com.squareup.okio:okio:3.x.y) even though we changed that artifact from JVM-only to Kotlin
      // Multiplatform. Note that module.json doesn't need this hack.
      val mavenPublications = publishingExtension.publications.withType<MavenPublication>()
      mavenPublications.configureEach {
        if (name != "jvm") return@configureEach
        val jvmPublication = this
        val kmpPublication = mavenPublications.getByName("kotlinMultiplatform")
        kmpPublication.pom.withXml {
          val root = asNode()
          val dependencies = (root["dependencies"] as NodeList).firstOrNull() as Node?
            ?: root.appendNode("dependencies")
          for (child in dependencies.children().toList()) {
            dependencies.remove(child as Node)
          }
          dependencies.appendNode("dependency").apply {
            appendNode("groupId", jvmPublication.groupId)
            appendNode("artifactId", jvmPublication.artifactId)
            appendNode("version", jvmPublication.version)
            appendNode("scope", "compile")
          }
        }
      }
    }
  }
}

subprojects {
  apply(plugin = "com.diffplug.spotless")
  configure<SpotlessExtension> {
    kotlin {
      target("**/*.kt")
      ktlint(libs.versions.ktlint.get())
    }
  }

  tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
      jvmTarget = JavaVersion.VERSION_1_8.toString()
      @Suppress("SuspiciousCollectionReassignment")
      freeCompilerArgs += "-Xjvm-default=all"
    }
  }

  tasks.withType<JavaCompile> {
    options.encoding = StandardCharsets.UTF_8.toString()
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
  }

  val testJavaVersion = System.getProperty("test.java.version", "19").toInt()
  tasks.withType<Test> {
    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    javaLauncher.set(javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(testJavaVersion))
    })

    testLogging {
      events(STARTED, PASSED, SKIPPED, FAILED)
      exceptionFormat = TestExceptionFormat.FULL
      showStandardStreams = false
    }

    if (loomEnabled) {
      jvmArgs = jvmArgs!! + listOf(
        "-Djdk.tracePinnedThread=full",
        "--enable-preview",
        "-DloomEnabled=true"
      )
    }
  }

  tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
  }

  normalization {
    runtimeClasspath {
      metaInf {
        ignoreAttribute("Bnd-LastModified")
      }
    }
  }
}

/**
 * Select a NodeJS version with WASI and WASM GC.
 * https://github.com/Kotlin/kotlin-wasm-examples/blob/main/wasi-example/build.gradle.kts
 */
plugins.withType<NodeJsRootPlugin> {
  extensions.getByType<NodeJsRootExtension>().apply {
    if (DefaultNativePlatform.getCurrentOperatingSystem().isWindows) {
      // We're waiting for a Windows build of NodeJS that can do WASM GC + WASI.
      nodeVersion = "21.4.0"
    } else {
      nodeVersion = "21.0.0-v8-canary202309143a48826a08"
      nodeDownloadBaseUrl = "https://nodejs.org/download/v8-canary"
    }
  }
  // Suppress an error because yarn doesn't like our Node version string.
  //   warning You are using Node "21.0.0-v8-canary202309143a48826a08" which is not supported and
  //   may encounter bugs or unexpected behavior.
  //   error typescript@5.0.4: The engine "node" is incompatible with this module.
  tasks.withType<KotlinNpmInstallTask>().all {
    args += "--ignore-engines"
  }
}

/**
 * Set the `OKIO_ROOT` environment variable for tests to access it.
 * https://publicobject.com/2023/04/16/read-a-project-file-in-a-kotlin-multiplatform-test/
 */
allprojects {
  tasks.withType<KotlinJvmTest>().configureEach {
    environment("OKIO_ROOT", rootDir)
  }

  tasks.withType<KotlinNativeTest>().configureEach {
    environment("SIMCTL_CHILD_OKIO_ROOT", rootDir)
    environment("OKIO_ROOT", rootDir)
  }

  tasks.withType<KotlinJsTest>().configureEach {
    environment("OKIO_ROOT", rootDir.toString())
  }
}
