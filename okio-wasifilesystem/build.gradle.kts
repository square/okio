import com.vanniktech.maven.publish.JavadocJar.Empty
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension

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

      const wasi = new WASI({
        version: 'preview1',
        args: argv,
        preopens: {
          '/tmp': '$base',
          '/a': '$baseA',
          '/b': '$baseB'
        },
        env,
      });

      const fs = await import('node:fs');
      const url = await import('node:url');
      const wasmBuffer = fs.readFileSync(url.fileURLToPath(import.meta.resolve('./okio-parent-okio-wasifilesystem-wasm-wasi-test.wasm')));
      const wasmModule = new WebAssembly.Module(wasmBuffer);
      const wasmInstance = new WebAssembly.Instance(wasmModule, wasi.getImportObject());

      wasi.initialize(wasmInstance);

      const exports = wasmInstance.exports

      export default new Proxy(exports, {
          _shownError: false,
          get(target, prop) {
              if (!this._shownError) {
                  this._shownError = true;
                  throw new Error("Do not use default import. Use the corresponding named import instead.")
              }
          }
      });
      export const {
          startUnitTests,
          _initialize,
          memory
      } = exports;
      """.trimIndent()
    )
  }
}
tasks.named("wasmWasiNodeTest").configure {
  dependsOn(injectWasiInit)
}
