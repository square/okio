plugins {
  kotlin("jvm")
  id("me.champeau.jmh")
}

jmh {
  includes.add("CompressBenchmark")
}

dependencies {
  api(projects.okio)
  api(libs.jmh.core)
  api("com.squareup.okio-zstd:okio-zstd:1.0.0-SNAPSHOT")
}
