import aQute.bnd.gradle.BundleTaskConvention
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension

plugins {
  kotlin("jvm")
  id("ru.vyarus.animalsniffer")
}

dependencies {
  implementation(project(":okio"))
  implementation(project(":okio-zipfilesystem"))

  implementation(deps.test.junit)
  implementation(deps.test.assertj)
  implementation(deps.kotlin.test.jdk)
}

tasks {
  val jar by getting(Jar::class) {
    val bndConvention = BundleTaskConvention(this)
    bndConvention.setBnd(
      """
      Export-Package: okio.resourcefilesystem
      Automatic-Module-Name: okio.resourcefilesystem
      Bundle-SymbolicName: com.squareup.okio.resourcefilesystem
      """
    )
    // Call the convention when the task has finished to modify the jar to contain OSGi metadata.
    doLast {
      bndConvention.buildBundle()
    }
  }
}

configure<AnimalSnifferExtension> {
  sourceSets = listOf(project.sourceSets.getByName("main"))
}

val signature by configurations.getting {
}

dependencies {
  signature(deps.animalSniffer.androidSignature)
  signature(deps.animalSniffer.javaSignature)
}
