rootProject.name = "okio-parent"

includeBuild("build-support")

include(":cursedmoshi")
include(":okio")
if (System.getProperty("kjs", "false").toBoolean()) {
  include(":okio-nodefilesystem")
}
include(":okio-testing-support")
include(":okio:jvm:jmh")
if (System.getProperty("kwasm", "false").toBoolean()) {
  include(":okio-wasifilesystem")
}
include(":samples")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
