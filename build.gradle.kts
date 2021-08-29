import aQute.bnd.gradle.BundleTaskConvention
import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  dependencies {
    classpath(deps.android.gradlePlugin)
    classpath(deps.kotlin.gradlePlugin)
    classpath(deps.animalSniffer.gradlePlugin)
    classpath(deps.japicmp)
    classpath(deps.dokka)
    classpath(deps.shadow)
    classpath(deps.jmh.gradlePlugin)
    classpath(deps.spotless)
    classpath(deps.bnd)
    // https://github.com/melix/japicmp-gradle-plugin/issues/36
    classpath(deps.guava)
  }

  repositories {
    mavenCentral()
    gradlePluginPortal()
    jcenter()
    google()
    maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
    maven(url = "https://kotlin.bintray.com/kotlinx/")
  }
}

// When scripts are applied the buildscript classes are not accessible directly therefore we save
// the class here to make it accessible.
ext.set("bndBundleTaskConventionClass", BundleTaskConvention::class.java)

allprojects {
  group = project.property("GROUP") as String
  version = project.property("VERSION_NAME") as String
}

subprojects {
  repositories {
    mavenCentral()
    jcenter()
    google()
    maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
    maven(url = "https://kotlin.bintray.com/kotlinx/")
  }

  apply(plugin = "com.diffplug.spotless")

  configure<SpotlessExtension> {
    kotlin {
      target("**/*.kt")
      ktlint(versions.ktlint).userData(mapOf("indent_size" to  "2"))
      trimTrailingWhitespace()
      endWithNewline()
    }
  }

  tasks.withType<KotlinCompile>().all {
    kotlinOptions {
      jvmTarget = "1.8"
      freeCompilerArgs += "-Xjvm-default=all"
    }
  }

  tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
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

  // This can't be in buildSrc due to a Dokka issue. https://github.com/Kotlin/dokka/issues/1463
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
      outputDirectory.set(file("${rootDir}/docs/2.x"))
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
}
