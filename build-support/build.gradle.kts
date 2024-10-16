plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
}

repositories {
  mavenCentral()
}

dependencies {
  add("compileOnly", kotlin("gradle-plugin"))
  add("compileOnly", kotlin("gradle-plugin-api"))
}

gradlePlugin {
  plugins {
    create("build-support") {
      id = "build-support"
      implementationClass = "BuildSupport"
    }
  }
}

dependencies {
  implementation(libs.kotlin.gradle.plugin)
}
