import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJs

plugins {
  kotlin("js")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
}

kotlin {
  js {
    configure(listOf(compilations["main"], compilations["test"])) {
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
    getByName("main") {
      dependencies {
        implementation(project(":okio"))
        // Uncomment this to generate fs.fs.module_node.kt. Use it when updating fs.kt.
        // implementation(npm("@types/node", "14.14.16", true))
      }
    }
    getByName("test") {
      dependencies {
        implementation(deps.kotlin.test.common)
        implementation(deps.kotlin.test.annotations)
        implementation(deps.kotlin.time)

        implementation(project(":okio-fakefilesystem"))
        implementation(project(":okio-testing-support"))
        implementation(deps.kotlin.test.js)
      }
    }
  }
}

mavenPublishing {
  configure(
    KotlinJs(javadocJar = Dokka("dokkaGfm"))
  )
}
