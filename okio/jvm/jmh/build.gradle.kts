plugins {
  kotlin("jvm")
  id("me.champeau.jmh")
}

jmh {
  includes.set(listOf(".*AsyncTimeout.*Benchmark.*"))
}

dependencies {
  api(projects.okio)
  api(libs.jmh.core)
}
