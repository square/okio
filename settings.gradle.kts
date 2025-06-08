rootProject.name = "okio-parent"

includeBuild("build-support")

include(":cursedmoshi")
include(":okio")
include(":okio-assetfilesystem")
include(":okio-bom")
include(":okio-fakefilesystem")
if (System.getProperty("kjs", "false").toBoolean()) {
  include(":okio-nodefilesystem")
}
include(":okio-testing-support")
include(":okio:jvm:jmh")
if (System.getProperty("kwasm", "false").toBoolean()) {
  include(":okio-wasifilesystem")
}
include(":samples")

// The Android test module doesn't work in IntelliJ. Use Android Studio or the command line.
if (System.getProperties().containsKey("android.injected.invoked.from.ide") ||
  System.getenv("ANDROID_HOME") != null) {
  include(":android-test")
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
