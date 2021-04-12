import java.awt.dnd.DropTarget

// If false - JS targets will not be configured in multiplatform projects.
val kmpJsEnabled = System.getProperty("kjs", "true").toBoolean()

// If false - Native targets will not be configured in multiplatform projects.
val kmpNativeEnabled = System.getProperty("knative", "true").toBoolean()

object versions {
  val kotlin = "1.4.20"
  val jmhPlugin = "0.5.0"
  val animalSnifferPlugin = "1.5.0"
  val dokka = "1.4.20"
  val jmh = "1.23"
  val animalSniffer = "1.16"
  val junit = "4.12"
  val assertj = "1.7.0"
  val shadowPlugin = "5.2.0"
  val spotless = "5.8.2"
  val ktlint = "0.40.0"
  val bndPlugin = "5.1.2"
}

object deps {
  object android {
    val gradlePlugin = "com.android.tools.build:gradle:4.1.1"
    val desugarJdkLibs = "com.android.tools:desugar_jdk_libs:1.1.1"
  }

  object androidx {
    val testExtJunit = "androidx.test.ext:junit:1.1.2"
    val testRunner = "androidx.test:runner:1.3.0"
  }

  object kotlin {
    val gradlePlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${versions.kotlin}"

    object stdLib {
      val common = "org.jetbrains.kotlin:kotlin-stdlib-common"
      val jdk8 = "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
      val js = "org.jetbrains.kotlin:kotlin-stdlib-js"
    }

    object test {
      val common = "org.jetbrains.kotlin:kotlin-test-common"
      val annotations = "org.jetbrains.kotlin:kotlin-test-annotations-common"
      val jdk = "org.jetbrains.kotlin:kotlin-test-junit"
      val js = "org.jetbrains.kotlin:kotlin-test-js"
    }

    val time = "org.jetbrains.kotlinx:kotlinx-datetime:0.1.1"
  }

  object jmh {
    val gradlePlugin = "me.champeau.gradle:jmh-gradle-plugin:${versions.jmhPlugin}"
    val core = "org.openjdk.jmh:jmh-core:${versions.jmh}"
    val generator = "org.openjdk.jmh:jmh-generator-annprocess:${versions.jmh}"
  }

  object animalSniffer {
    val gradlePlugin = "ru.vyarus:gradle-animalsniffer-plugin:${versions.animalSnifferPlugin}"
    val annotations = "org.codehaus.mojo:animal-sniffer-annotations:${versions.animalSniffer}"
    val androidSignature = "net.sf.androidscents.signature:android-api-level-15:4.0.3_r5@signature"
    val javaSignature = "org.codehaus.mojo.signature:java17:1.0@signature"
  }

  val japicmp = "me.champeau.gradle:japicmp-gradle-plugin:0.2.8"
  val dokka = "org.jetbrains.dokka:dokka-gradle-plugin:${versions.dokka}"
  val shadow = "com.github.jengelman.gradle.plugins:shadow:${versions.shadowPlugin}"
  val spotless = "com.diffplug.spotless:spotless-plugin-gradle:${versions.spotless}"
  val bnd = "biz.aQute.bnd:biz.aQute.bnd.gradle:${versions.bndPlugin}"

  object test {
    val junit = "junit:junit:${versions.junit}"
    val assertj = "org.assertj:assertj-core:${versions.assertj}"
  }
}
