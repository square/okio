rootProject.name = "okio-parent"

include(":okio")
include(":okio-fakefilesystem")
if (System.getProperty("kjs", "true").toBoolean()) {
  include(":okio-nodefilesystem")
}
include(":okio:jvm:japicmp")
include(":okio:jvm:jmh")
include(":samples")

enableFeaturePreview("GRADLE_METADATA")

// The Android test module doesn't work in IntelliJ. Use Android Studio or the command line.
if (System.getProperties().containsKey("android.injected.invoked.from.ide") ||
  System.getenv("ANDROID_SDK_ROOT") != null) {
  include(":android-test")
}
