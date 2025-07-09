plugins {
  kotlin("jvm")
  id("me.champeau.jmh")
}

jmh {
  this.includes.addAll(
    "AsyncTimeoutBenchmark"
  )
}

dependencies {
  api(projects.okio)
  api(libs.jmh.core)
}
