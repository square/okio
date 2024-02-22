plugins {
  kotlin("multiplatform")
  id("build-support")
}

kotlin {
  configureOrCreateOkioPlatforms()

  sourceSets {
    all {
      languageSettings.apply {
        optIn("kotlin.time.ExperimentalTime")
      }
    }

    val commonMain by getting {
      dependencies {
        api(projects.okio)
        api(libs.kotlin.test)
      }
    }

    val nonWasmMain by creating {
      dependsOn(commonMain)
      dependencies {
        api(libs.kotlin.time)
        implementation(projects.okioFakefilesystem)
      }
    }

    val zlibMain by creating {
      dependsOn(commonMain)
    }

    if (kmpJsEnabled) {
      val jsMain by getting {
        dependsOn(nonWasmMain)
      }
    }

    val jvmMain by getting {
      dependsOn(nonWasmMain)
      dependsOn(zlibMain)
      dependencies {
        // On the JVM the kotlin-test library resolves to one of three implementations based on
        // which testing framework is in use. JUnit is used downstream, but Gradle can't know that
        // here and thus fails to select a variant automatically. Declare it manually instead.
        api(libs.kotlin.test.junit)
      }
    }

    if (kmpNativeEnabled) {
      createSourceSet("nativeMain", children = nativeTargets)
        .also { nativeMain ->
          nativeMain.dependsOn(nonWasmMain)
          nativeMain.dependsOn(zlibMain)
        }
    }

    if (kmpWasmEnabled) {
      createSourceSet("wasmMain", children = wasmTargets)
        .also { wasmMain ->
          wasmMain.dependsOn(commonMain)
        }
    }
  }
}
