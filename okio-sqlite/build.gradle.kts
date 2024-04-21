plugins {
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
  id("build-support")
}

kotlin {
  jvm {
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(projects.okio)
        api(projects.okioFakefilesystem)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(projects.okioTestingSupport)
      }
    }

    val jvmMain by getting {
      dependencies {
        implementation(libs.sqlite.jdbc)
      }
    }
  }
}
