plugins {
  kotlin("multiplatform")
}

kotlin {
  js(BOTH) {
    configure(listOf(compilations.getByName("main"), compilations.getByName("test"))) {
      tasks.getByName(compileKotlinTaskName) {
        kotlinOptions {
          moduleKind = "umd"
          sourceMap = true
          metaInfo = true
        }
      }
    }
    nodejs {
      testTask {
        useMocha {
          timeout = "30s"
        }
      }
    }
  }
  sourceSets {
    all {
      languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
    }
    val jsMain by getting {
      dependencies {
        implementation(project(":okio"))
        api(deps.kotlin.stdLib.common)
        // Uncomment this to generate fs.fs.module_node.kt. Use it when updating fs.kt.
        // implementation(npm("@types/node", "14.14.16", true))
        api(deps.kotlin.stdLib.js)
      }
    }
    val jsTest by getting {
      dependencies {
        implementation(deps.kotlin.test.common)
        implementation(deps.kotlin.test.annotations)
        implementation(deps.kotlin.time)

        implementation(project(":okio-fakefilesystem"))
        implementation(deps.kotlin.test.js)
      }
    }
  }
}

apply(from = "$rootDir/gradle/gradle-mvn-mpp-push.gradle")
