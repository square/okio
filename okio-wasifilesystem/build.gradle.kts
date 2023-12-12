import com.vanniktech.maven.publish.JavadocJar.Empty
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

plugins {
  kotlin("multiplatform")
  // TODO: Restore Dokka once this issue is resolved.
  //     https://github.com/Kotlin/dokka/issues/3038
  // id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("build-support")
  id("binary-compatibility-validator")
}

kotlin {
  configureOrCreateWasmPlatform(
    js = false,
    wasi = true,
  )
  sourceSets {
    all {
      languageSettings.optIn("kotlin.wasm.unsafe.UnsafeWasmMemoryApi")
    }
    val wasmWasiMain by getting {
      dependencies {
        implementation(projects.okio)
      }
    }
    val wasmWasiTest by getting {
      dependencies {
        implementation(projects.okioTestingSupport)
        implementation(libs.kotlin.test)
      }
    }
  }
}

configure<MavenPublishBaseExtension> {
  // TODO: switch from 'Empty' to 'Dokka' once this issue is resolved.
  //     https://github.com/Kotlin/dokka/issues/3038
  configure(
    KotlinMultiplatform(javadocJar = Empty()),
  )
}

/**
 * Inspired by runner.mjs in kowasm, this rewrites the JavaScript bootstrap script to set up WASI.
 *
 * See also:
 *  * https://github.com/kowasm/kowasm
 *  * https://github.com/nodejs/node/blob/main/doc/api/wasi.md
 *
 * This task overwrites the output of `compileTestDevelopmentExecutableKotlinWasmWasi` and must run
 * after that task. It must also run before the WASM test execution tasks that read this script.
 *
 * Note that this includes which file paths are exposed to the WASI sandbox.
 */
val injectWasiInit by tasks.creating {
  dependsOn("compileTestDevelopmentExecutableKotlinWasmWasi")
  val moduleName = "${rootProject.name}-${project.name}-wasm-wasi-test"

  val entryPointMjs = File(
    buildDir,
    "compileSync/wasmWasi/test/testDevelopmentExecutable/kotlin/$moduleName.mjs"
  )

  outputs.file(entryPointMjs)

  doLast {
    val base = File(System.getProperty("java.io.tmpdir"), "okio-wasifilesystem-test")
    val baseA = File(base, "a")
    val baseB = File(base, "b")
    base.mkdirs()
    baseA.mkdirs()
    baseB.mkdirs()

    entryPointMjs.writeText(
      """
      import { WASI } from 'wasi';
      import { argv, env } from 'node:process';

      export const wasi = new WASI({
        version: 'preview1',
        preopens: {
          '/tmp': '$base',
          '/a': '$baseA',
          '/b': '$baseB'
        }
      });

      const module = await import(/* webpackIgnore: true */'node:module');
      const require = module.default.createRequire(import.meta.url);
      const fs = require('fs');
      const path = require('path');
      const url = require('url');
      const filepath = url.fileURLToPath(import.meta.url);
      const dirpath = path.dirname(filepath);
      const wasmBuffer = fs.readFileSync(path.resolve(dirpath, './$moduleName.wasm'));
      const wasmModule = new WebAssembly.Module(wasmBuffer);
      const wasmInstance = new WebAssembly.Instance(wasmModule, wasi.getImportObject());

      wasi.initialize(wasmInstance);

      export default wasmInstance.exports;
      """.trimIndent()
    )
  }
}
tasks.withType<KotlinJsTest>().configureEach {
  // TODO: get this working on Windows.
  //     > command 'C:\Users\runneradmin\.gradle\nodejs\node-v20.0.0-win-x64\node.exe'
  //       exited with errors (exit code: 1)
  onlyIf {
    val os = DefaultNativePlatform.getCurrentOperatingSystem()
    !os.isWindows
  }
  nodeJsArgs += "--experimental-wasi-unstable-preview1"
}
tasks.named("wasmWasiNodeTest").configure {
  dependsOn(injectWasiInit)
}
