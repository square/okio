plugins {
  kotlin("multiplatform")
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
    createNativePlatforms()
  }
  sourceSets {
    commonMain {
      dependencies {
        api(deps.kotlin.stdLib.common)
        api(deps.kotlin.time)
        api(project(":okio"))
        implementation(deps.kotlin.test.common)
        implementation(deps.kotlin.test.annotations)
        implementation(project(":okio-fakefilesystem"))
      }
    }
    val jvmMain by getting {
      dependencies {
        implementation(deps.kotlin.test.jdk)
      }
    }
    if (kmpJsEnabled) {
      val jsMain by getting {
        dependencies {
          implementation(deps.kotlin.test.js)
        }
      }
    }
    if (kmpNativeEnabled) {
      val nativeMain by creating {
        mainSourceSets(nativePlatforms).dependsOn(this)
      }
    }
  }
}
