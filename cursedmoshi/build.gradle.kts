import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  id("com.vanniktech.maven.publish.base")
  id("org.jetbrains.dokka")
}

// We use newer JDKs but target 16 for maximum compatibility
val service = project.extensions.getByType<JavaToolchainService>()
val customLauncher =
  service.launcherFor {
    languageVersion.set(libs.versions.jdk.map(JavaLanguageVersion::of))
  }

// Package our actual RecordJsonAdapter from java16 sources in and denote it as an MRJAR
tasks.named<Jar>("jar") {
  manifest {
    attributes("Multi-Release" to "true")
  }
}

tasks.withType<Test>().configureEach {
  // ExtendsPlatformClassWithProtectedField tests a case where we set a protected ByteArrayOutputStream.buf field
  jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
}

tasks
  .withType<KotlinCompile>()
  .configureEach {
    compilerOptions {
      freeCompilerArgs.addAll(
        "-opt-in=kotlin.contracts.ExperimentalContracts",
        "-Xjvm-default=all",
      )
      if (name.contains("test", true)) {
        freeCompilerArgs.add("-opt-in=kotlin.ExperimentalStdlibApi")
      }
    }
  }

dependencies {
  compileOnly(libs.jsr305)
  compileOnly(libs.kotlinx.coroutines.core)
  api(project(":cursedokio"))

  testCompileOnly(libs.jsr305)
}

tasks.withType<Jar>().configureEach {
  manifest {
    attributes("Automatic-Module-Name" to "com.squareup.cursedmoshi")
  }
}
