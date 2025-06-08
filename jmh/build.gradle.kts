plugins {
  kotlin("jvm")
  id("me.champeau.jmh")
}

jmh {
  this.includes.add("com.squareup.okio.benchmarks.ReadJsonBenchmark")
}

dependencies {
  api(projects.okio)
  api(libs.jmh.core)
  api(libs.kotlinx.coroutines.core)
  api(project(":cursedmoshi"))
}
