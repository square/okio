plugins {
  kotlin("multiplatform")
  id("build-support")
}

kotlin {
  if (kmpJsEnabled) {
    jvm()
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
      browser()
    }
  }
  if (kmpNativeEnabled) {
    configureOrCreateNativePlatforms()
  }
  sourceSets {
    commonMain {
      dependencies {
        api(deps.kotlin.time)
        api(project(":okio"))
        implementation(deps.kotlin.test.common)
        implementation(deps.kotlin.test.annotations)
        implementation(project(":okio-fakefilesystem"))
      }
    }
    getByName("jvmMain") {
      dependencies {
        implementation(deps.kotlin.test.jdk)
      }
    }
    if (kmpJsEnabled) {
      getByName("jsMain") {
        dependencies {
          implementation(deps.kotlin.test.js)
        }
      }
    }
    if (kmpNativeEnabled) {
      createSourceSet("nativeMain", children = nativeTargets)
    }
  }
}
