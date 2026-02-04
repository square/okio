import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  id("com.android.library")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("binary-compatibility-validator")
  id("build-support")
}

android {
  namespace = "okio.assetfilesystem"
  compileSdk = 33

  defaultConfig {
    minSdk = 14

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
}

dependencies {
  api(projects.okio)

  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.kotlin.test)
  androidTestImplementation(libs.test.assertk)
}

configure<MavenPublishBaseExtension> {
  configure(
    AndroidSingleVariantLibrary()
  )
}
