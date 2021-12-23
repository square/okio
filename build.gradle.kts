import aQute.bnd.gradle.BundleTaskConvention
import com.diffplug.gradle.spotless.SpotlessExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import java.nio.charset.StandardCharsets
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("build-support").apply(false)
}

buildscript {
  dependencies {
    classpath(deps.android.gradlePlugin)
    classpath(deps.animalSniffer.gradlePlugin)
    classpath(deps.japicmp)
    classpath(deps.dokka)
    classpath(deps.shadow)
    classpath(deps.jmh.gradlePlugin)
    classpath(deps.spotless)
    classpath(deps.bnd)
    // https://github.com/melix/japicmp-gradle-plugin/issues/36
    classpath(deps.guava)
    classpath(deps.vanniktechPublishPlugin)
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
ext.set("bndBundleTaskConventionClass", BundleTaskConvention::class.java)

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
    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(SonatypeHost.S01)
      signAllPublications()
      pom {
        description.set("A modern I/O API for Java")
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
    }
  }
}

subprojects {
  apply(plugin = "com.diffplug.spotless")
  configure<SpotlessExtension> {
    kotlin {
      target("**/*.kt")
      ktlint(versions.ktlint).userData(mapOf("indent_size" to "2"))
      trimTrailingWhitespace()
      endWithNewline()
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

  tasks.withType<Test> {
    testLogging {
      events(STARTED, PASSED, SKIPPED, FAILED)
      exceptionFormat = TestExceptionFormat.FULL
      showStandardStreams = false
    }
  }
}
