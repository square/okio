import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJs
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("js")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
}

kotlin {
  js {
    configure(listOf(compilations.getByName("main"), compilations.getByName("test"))) {
      tasks.getByName(compileKotlinTaskName) {
        kotlinOptions {
          moduleKind = "umd"
          sourceMap = true
          metaInfo = true
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
    val main by getting {
      dependencies {
        implementation(projects.okio)
        // Uncomment this to generate fs.fs.module_node.kt. Use it when updating fs.kt.
        // implementation(npm("@types/node", "14.14.16", true))
      }
    }
    val test by getting {
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
    KotlinJs(javadocJar = Dokka("dokkaGfm"))
  )
}
