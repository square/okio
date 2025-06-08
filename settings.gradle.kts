rootProject.name = "okio-parent"

includeBuild("build-support")

include(":cursedmoshi")
include(":cursedokio")
include(":moshi")
include(":okio")
include(":jmh")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
