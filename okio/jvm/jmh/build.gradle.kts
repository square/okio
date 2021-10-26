import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.github.jengelman.gradle.plugins.shadow.transformers.DontIncludeResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.IncludeResourceTransformer

plugins {
  `java-library`
  kotlin("jvm")
  id("com.github.johnrengelman.shadow")
  id("me.champeau.gradle.jmh")
}

jmh {
  jvmArgs = listOf("-Djmh.separateClasspathJAR=true")
  include = listOf("""com\.squareup\.okio\.benchmarks\.MessageDigestBenchmark.*""")
  duplicateClassesStrategy = DuplicatesStrategy.WARN
}

dependencies {
  api(project(":okio"))
  api(deps.jmh.core)
  jmh(project(path = ":okio", configuration = "jvmRuntimeElements"))
  jmh(deps.jmh.core)
  jmh(deps.jmh.generator)
}

tasks.jmhJar {
  transform(DontIncludeResourceTransformer().apply {
    resource = "META-INF/BenchmarkList"
  })

  transform(IncludeResourceTransformer().apply {
    resource = "META-INF/BenchmarkList"
    file = file("${project.buildDir}/jmh-generated-resources/META-INF/BenchmarkList")
  })
}
tasks.assemble.dependsOn(tasks.jmhJar)
