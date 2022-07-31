plugins {
  kotlin("multiplatform")
  application
}

application {
  mainClass.set(System.getProperty("mainClass"))
}

kotlin {
  jvm {
    withJava()
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
