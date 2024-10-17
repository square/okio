import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.AndroidLintTask

plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
}

buildscript {
  repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
  }
}

val isIDE = properties.containsKey("android.injected.invoked.from.ide") ||
  (System.getenv("XPC_SERVICE_NAME") ?: "").contains("intellij") ||
  System.getenv("IDEA_INITIAL_DIRECTORY") != null

android {
  namespace = "com.squareup.okio"

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    isCoreLibraryDesugaringEnabled = true
  }

  kotlinOptions {
    freeCompilerArgs += "-Xmulti-platform"
  }

  compileSdkVersion(33)

  defaultConfig {
    minSdkVersion(15)
    targetSdkVersion(33)
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // AndroidJUnitRunner wasn't finding tests in multidex artifacts when running on Android 4.0.3.
    // Work around by adding all Okio classes to the keep list. That way they'll be in the main
    // .dx file where TestRequestBuilder will find them.
    multiDexEnabled = true
    multiDexKeepProguard = file("multidex-config.pro")
  }

  if (!isIDE) {
    sourceSets {
      named("androidTest") {
        java.srcDirs(
          project.file("../okio-fakefilesystem/src/commonMain/kotlin"),
          project.file("../okio/src/commonMain/kotlin"),
          project.file("../okio/src/commonTest/java"),
          project.file("../okio/src/commonTest/kotlin"),
          project.file("../okio/src/hashFunctions/kotlin"),
          project.file("../okio/src/jvmMain/kotlin"),
          project.file("../okio/src/jvmTest/java"),
          project.file("../okio/src/jvmTest/kotlin")
        )
      }
    }
  }
}

// https://issuetracker.google.com/issues/325146674
tasks.withType<AndroidLintAnalysisTask> {
  onlyIf { false }
}

dependencies {
  coreLibraryDesugaring(libs.android.desugar.jdk.libs)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.kotlin.test)
  androidTestImplementation(libs.kotlin.time)
  androidTestImplementation(libs.test.assertj)
  androidTestImplementation(libs.test.junit)
}
