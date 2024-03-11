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

  if (kmpNativeEnabled) {
    linuxX64()
    linuxArm64()
    macosArm64()
    macosX64()
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.okio)
      }
    }
    commonTest {
      dependencies {
        implementation(libs.kotlin.test)
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
