import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension

plugins {
  kotlin("multiplatform")
  id("ru.vyarus.animalsniffer")
}

kotlin {
  jvm {
    withJava()
  }
  sourceSets {
    commonMain {
      dependencies {
        api(deps.kotlin.stdLib.common)
        api(deps.kotlin.time)
        api(project(":okio"))
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(deps.test.junit)
        implementation(deps.test.assertj)
        implementation(deps.kotlin.test.jdk)
        implementation(deps.kotlin.time)
      }
    }
  }
}

tasks {
  val jvmJar by getting(Jar::class) {
    val bndConvention = aQute.bnd.gradle.BundleTaskConvention(this)
    bndConvention.setBnd(
      """
      Export-Package: okio.zipfilesystem
      Automatic-Module-Name: okio.zipfilesystem
      Bundle-SymbolicName: com.squareup.okio.zipfilesystem
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

apply(from = "$rootDir/gradle/gradle-mvn-mpp-push.gradle")
