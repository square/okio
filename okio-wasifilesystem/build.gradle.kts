import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

plugins {
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("build-support")
  id("binary-compatibility-validator")
}

kotlin {
  configureOrCreateWasmPlatform()
  sourceSets {
    val wasmMain by getting {
      dependencies {
        implementation(projects.okio)
      }
    }
    val wasmTest by getting {
      dependencies {
        implementation(projects.okioTestingSupport)
        implementation(libs.kotlin.test)
      }
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinMultiplatform(javadocJar = Dokka("dokkaGfm")),
  )
}

/**
 * Inspired by runner.mjs in kowasm, this rewrites the JavaScript bootstrap script to set up WASI.
 *
 * See also:
 *  * https://github.com/kowasm/kowasm
 *  * https://github.com/nodejs/node/blob/main/doc/api/wasi.md
 *
 * This task overwrites the output of `compileTestKotlinWasm` and must run after that task. It
 * must also run before the WASM test execution tasks that read this script.
 *
 * Note that this includes which file paths are exposed to the WASI sandbox.
 */
val injectWasiInit by tasks.creating {
  dependsOn("compileTestKotlinWasm")
  val moduleName = "${rootProject.name}-${project.name}-wasm-test"

  val entryPointMjs = File(
    buildDir,
    "compileSync/wasm/test/testDevelopmentExecutable/kotlin/$moduleName.mjs"
  )

  outputs.file(entryPointMjs)

  doLast {
    val tmpdir = File(System.getProperty("java.io.tmpdir"), "okio-wasifilesystem-test")
    tmpdir.mkdirs()

    entryPointMjs.writeText(
      """
      import {instantiate} from './$moduleName.uninstantiated.mjs';
      import {WASI} from "wasi";

      export const wasi = new WASI({
        version: 'preview1',
        preopens: {
          '/tmp': '$tmpdir'
        }
      });

      const {instance, exports} = await instantiate({wasi_snapshot_preview1: wasi.wasiImport});

      wasi.initialize(instance);

      export default exports;
      """.trimIndent()
    )
  }
}
tasks.withType<KotlinJsTest>().configureEach {
  nodeJsArgs += "--experimental-wasi-unstable-preview1"
}
tasks.named("wasmTestTestDevelopmentExecutableCompileSync").configure {
  dependsOn(injectWasiInit)
}
tasks.named("wasmTestTestProductionExecutableCompileSync").configure {
  dependsOn(injectWasiInit)
}
