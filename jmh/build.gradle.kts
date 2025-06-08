plugins {
  kotlin("jvm")
  id("me.champeau.jmh")
}

jmh {
  this.includes.add("com.squareup.okio.benchmarks.ReadJsonBenchmark")
}

dependencies {
  api(libs.jmh.core)
  api(libs.kotlinx.coroutines.core)
  api(project(":okio"))
  api(project(":moshi"))
  api(project(":cursedokio"))
  api(project(":cursedmoshi"))
}
