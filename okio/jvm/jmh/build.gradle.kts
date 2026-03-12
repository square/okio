plugins {
  kotlin("jvm")
  id("me.champeau.jmh")
}

jmh {
  includes.set(listOf(".*AsyncTimeoutBenchmark.*"))
}

dependencies {
  api(projects.okio)
  api(libs.jmh.core)
}
