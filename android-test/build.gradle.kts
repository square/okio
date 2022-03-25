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
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    isCoreLibraryDesugaringEnabled = true
  }

  kotlinOptions {
    freeCompilerArgs += "-Xmulti-platform"
  }

  compileSdkVersion(30)

  defaultConfig {
    minSdkVersion(15)
    targetSdkVersion(30)
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

dependencies {
  coreLibraryDesugaring(deps.android.desugarJdkLibs)
  androidTestImplementation(deps.androidx.testExtJunit)
  androidTestImplementation(deps.androidx.testRunner)
  androidTestImplementation(deps.animalSniffer.annotations)
  androidTestImplementation(deps.kotlin.test)
  androidTestImplementation(deps.kotlin.time)
  androidTestImplementation(deps.test.assertj)
  androidTestImplementation(deps.test.junit)
}
