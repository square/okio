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
      languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
    }
    val main by getting {
      dependencies {
        implementation(project(":okio"))
        implementation(project(":okio-testing-support"))
        api(deps.kotlin.stdLib.common)
        // Uncomment this to generate fs.fs.module_node.kt. Use it when updating fs.kt.
        // implementation(npm("@types/node", "14.14.16", true))
        api(deps.kotlin.stdLib.js)
      }
    }
    val test by getting {
      dependencies {
        implementation(deps.kotlin.test.common)
        implementation(deps.kotlin.test.annotations)
        implementation(deps.kotlin.time)

        implementation(project(":okio-fakefilesystem"))
        implementation(deps.kotlin.test.js)
      }
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJs(javadocJar = Dokka("dokkaGfm"))
  )
}
