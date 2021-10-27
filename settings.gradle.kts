rootProject.name = "okio-parent"

includeBuild("build-support")

include(":okio")
include(":okio-bom")
include(":okio-fakefilesystem")
if (System.getProperty("kjs", "true").toBoolean()) {
  include(":okio-nodefilesystem")
}
include(":okio-testing-support")
include(":okio:jvm:japicmp")
include(":okio:jvm:jmh")
include(":samples")

// The Android test module doesn't work in IntelliJ. Use Android Studio or the command line.
if (System.getProperties().containsKey("android.injected.invoked.from.ide") ||
  System.getenv("ANDROID_SDK_ROOT") != null) {
  include(":android-test")
}
