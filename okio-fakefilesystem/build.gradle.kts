import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension

plugins {
  kotlin("multiplatform")
  id("ru.vyarus.animalsniffer")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("build-support")
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
      browser {
      }
    }
  }
  if (kmpNativeEnabled) {
    configureOrCreateNativePlatforms()
  }
  sourceSets {
    commonMain {
      dependencies {
        api(libs.kotlin.time)
        api(projects.okio)
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

val signature: Configuration by configurations

dependencies {
  signature(variantOf(libs.animalSniffer.android) { artifactType("signature") })
  signature(variantOf(libs.animalSniffer.java) { artifactType("signature") })
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinMultiplatform(javadocJar = Dokka("dokkaGfm"))
  )
}
