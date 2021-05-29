import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension

plugins {
  kotlin("multiplatform")
  id("ru.vyarus.animalsniffer")
}

kotlin {
  jvm {
    withJava()
  }
  if (kmpJsEnabled) {
    js {
      compilations.all {
        kotlinOptions {
          moduleKind = "umd"
          sourceMap = true
          metaInfo = true
        }
      }
      nodejs {
        testTask {
          useMocha {
            timeout = "30s"
          }
        }
      }
    }
  }
  if (kmpNativeEnabled) {
    iosX64()
    iosArm64()
    tvosX64()
    tvosArm64()
    watchosArm32()
    watchosArm64()
    watchosX86()
    // Required to generate tests tasks: https://youtrack.jetbrains.com/issue/KT-26547
    linuxX64()
    macosX64()
    mingwX64()
  }
  sourceSets {
    commonMain {
      dependencies {
        api(deps.kotlin.stdLib.common)
        api(deps.kotlin.time)
        api(project(":okio"))
      }
    }
  }
}

tasks {
  val jvmJar by getting(Jar::class) {
    val bndConvention = aQute.bnd.gradle.BundleTaskConvention(this)
    bndConvention.setBnd(
      """
      Export-Package: okio.fakefilesystem
      Automatic-Module-Name: okio.fakefilesystem
      Bundle-SymbolicName: com.squareup.okio.fakefilesystem
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

apply(plugin = "okio-publishing")
