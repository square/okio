plugins {
  kotlin("jvm")
  id("me.champeau.jmh")
}

jmh {
}

dependencies {
  api(projects.okio)
  api(libs.jmh.core)
}
