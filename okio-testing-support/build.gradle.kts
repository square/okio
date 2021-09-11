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
    iosX64()
    iosArm64()
    tvosX64()
    tvosArm64()
    watchosArm32()
    watchosArm64()
    watchosX86()
    // Required to generate tests tasks: https://youtrack.jetbrains.com/issue/KT-26547
    linuxX64()
    macosX64()
    mingwX64()
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
      val nativeMain by creating {}
      val iosX64Main by getting {}
      val iosArm64Main by getting {}
      val tvosX64Main by getting {}
      val tvosArm64Main by getting {}
      val watchosArm32Main by getting {}
      val watchosArm64Main by getting {}
      val watchosX86Main by getting {}
      val linuxX64Main by getting {}
      val macosX64Main by getting {}
      val mingwX64Main by getting {}
      val nativeMains = listOf(
        mingwX64Main,
        iosX64Main,
        iosArm64Main,
        macosX64Main,
        tvosX64Main,
        tvosArm64Main,
        watchosArm32Main,
        watchosArm64Main,
        watchosX86Main,
        linuxX64Main,
      )
      for (it in nativeMains) {
        it.dependsOn(nativeMain)
      }
    }
  }
}
