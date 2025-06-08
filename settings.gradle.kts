rootProject.name = "okio-parent"

includeBuild("build-support")

include(":cursedmoshi")
include(":cursedokio")
include(":jmh")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
