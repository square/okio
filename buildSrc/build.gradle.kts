plugins {
  `kotlin-dsl`
}

repositories {
  jcenter()
}

dependencies {
  api(kotlin("gradle-plugin"))
  api(kotlin("gradle-plugin-api"))
}
