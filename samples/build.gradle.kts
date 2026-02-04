plugins {
  kotlin("multiplatform")
}

kotlin {
  jvmToolchain(24)
  jvm {
    binaries {
      executable {
        mainClass.set(System.getProperty("mainClass"))
      }
    }
  }
  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.okio)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(libs.test.junit)
        implementation(libs.test.assertj)
      }
    }
  }
}
