plugins {
  kotlin("multiplatform")
  id("build-support")
}

kotlin {
  jvm {
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
    configureOrCreateNativePlatforms()
  }
  sourceSets {
    all {
      languageSettings.apply {
        optIn("kotlin.time.ExperimentalTime")
      }
    }

    commonMain {
      dependencies {
        api(libs.kotlin.time)
        api(projects.okio)
        api(libs.kotlin.test)
        implementation(projects.okioFakefilesystem)
      }
    }
    getByName("jvmMain") {
      dependencies {
        // On the JVM the kotlin-test library resolves to one of three implementations based on
        // which testing framework is in use. JUnit is used downstream, but Gradle can't know that
        // here and thus fails to select a variant automatically. Declare it manually instead.
        api(libs.kotlin.test.junit)
      }
    }
    if (kmpNativeEnabled) {
      createSourceSet("nativeMain", children = nativeTargets)
    }
  }
}
