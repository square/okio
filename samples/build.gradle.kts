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
        implementation(project(":okio"))
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(deps.test.junit)
        implementation(deps.test.assertj)
      }
    }
  }
}
