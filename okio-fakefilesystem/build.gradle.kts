import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("binary-compatibility-validator")
  id("build-support")
}

kotlin {
  jvm {
  }
  if (kmpJsEnabled) {
    js {
      compilations.all {
        kotlinOptions {
          moduleKind = "umd"
          sourceMap = true
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
    val commonMain by getting {
      dependencies {
        api(libs.kotlin.time)
        api(projects.okio)
      }
    }
    val commonTest by getting
    if (kmpWasmEnabled) {
      // Add support for wasmWasi when https://github.com/Kotlin/kotlinx-datetime/issues/324 is resolved.
      configureOrCreateWasmPlatform(wasi = false)
      createSourceSet("wasmMain", parent = commonMain, children = listOf("wasmJs"))
      createSourceSet("wasmTest", parent = commonTest, children = listOf("wasmJs"))
    }
  }
}

tasks {
  val jvmJar by getting(Jar::class) {
    // BundleTaskConvention() crashes unless there's a 'main' source set.
    sourceSets.create(SourceSet.MAIN_SOURCE_SET_NAME)
    val bndExtension = aQute.bnd.gradle.BundleTaskExtension(this)
    bndExtension.setBnd(
      """
      Export-Package: okio.fakefilesystem
      Automatic-Module-Name: okio.fakefilesystem
      Bundle-SymbolicName: com.squareup.okio.fakefilesystem
      """
    )
    // Call the convention when the task has finished to modify the jar to contain OSGi metadata.
    doLast {
      bndExtension.buildAction()
        .execute(this)
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinMultiplatform(javadocJar = Dokka("dokkaGfm"))
  )
}
