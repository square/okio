// If false - JS targets will not be configured in multiplatform projects.
val kmpJsEnabled = System.getProperty("kjs", "true").toBoolean()

// If false - Native targets will not be configured in multiplatform projects.
val kmpNativeEnabled = System.getProperty("knative", "true").toBoolean()

object versions {
  const val jmh = "1.33"
  const val ktlint = "0.42.1"
}

object deps {
  object android {
    const val gradlePlugin = "com.android.tools.build:gradle:7.1.2"
    const val desugarJdkLibs = "com.android.tools:desugar_jdk_libs:1.1.5"
  }

  object androidx {
    const val testExtJunit = "androidx.test.ext:junit:1.1.3"
    const val testRunner = "androidx.test:runner:1.4.0"
  }

  object kotlin {
    const val test = "org.jetbrains.kotlin:kotlin-test"
    const val testJunit = "org.jetbrains.kotlin:kotlin-test-junit"
    const val time = "org.jetbrains.kotlinx:kotlinx-datetime:0.3.1"
  }

  object jmh {
    const val gradlePlugin = "me.champeau.gradle:jmh-gradle-plugin:0.5.3"
    const val core = "org.openjdk.jmh:jmh-core:${versions.jmh}"
    const val generator = "org.openjdk.jmh:jmh-generator-annprocess:${versions.jmh}"
  }

  object animalSniffer {
    const val gradlePlugin = "ru.vyarus:gradle-animalsniffer-plugin:1.5.3"
    const val annotations = "org.codehaus.mojo:animal-sniffer-annotations:1.20"
    const val androidSignature = "net.sf.androidscents.signature:android-api-level-15:4.0.3_r5@signature"
    const val javaSignature = "org.codehaus.mojo.signature:java17:1.0@signature"
  }

  const val japicmp = "me.champeau.gradle:japicmp-gradle-plugin:0.3.0"
  const val dokka = "org.jetbrains.dokka:dokka-gradle-plugin:1.5.31"
  const val shadow = "gradle.plugin.com.github.johnrengelman:shadow:7.1.0"
  const val spotless = "com.diffplug.spotless:spotless-plugin-gradle:5.16.0"
  const val bnd = "biz.aQute.bnd:biz.aQute.bnd.gradle:6.0.0"
  const val guava = "com.google.guava:guava:31.0.1-jre"
  const val vanniktechPublishPlugin = "com.vanniktech:gradle-maven-publish-plugin:0.18.0"

  object test {
    const val junit = "junit:junit:4.13.2"
    const val assertj = "org.assertj:assertj-core:3.21.0"
  }
}
