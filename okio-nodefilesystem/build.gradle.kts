import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("binary-compatibility-validator")
}

kotlin {
  js {
    configure(listOf(compilations.getByName("main"), compilations.getByName("test"))) {
      tasks.getByName(compileKotlinTaskName) {
        kotlinOptions {
          moduleKind = "umd"
          sourceMap = true
        }
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
  sourceSets {
    all {
      languageSettings.optIn("kotlin.RequiresOptIn")
    }
    matching { it.name.endsWith("Test") }.all {
      languageSettings {
        optIn("kotlin.time.ExperimentalTime")
      }
    }
    commonMain {
      dependencies {
        implementation(projects.okio)
        // Uncomment this to generate fs.fs.module_node.kt. Use it when updating fs.kt.
        // implementation(npm("@types/node", "14.14.16", true))
      }
    }
    commonTest {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.kotlin.time)

        implementation(projects.okioFakefilesystem)
        implementation(projects.okioTestingSupport)
      }
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinMultiplatform(javadocJar = Dokka("dokkaGfm"))
  )
}
