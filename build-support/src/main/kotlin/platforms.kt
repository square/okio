import org.gradle.api.NamedDomainObjectContainer
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

fun KotlinMultiplatformExtension.configureOrCreateNativePlatforms() {
  iosX64()
  iosArm64()
  iosSimulatorArm64()
  tvosX64()
  tvosArm64()
  tvosSimulatorArm64()
  watchosArm32()
  watchosArm64()
  watchosX86()
  watchosX64()
  watchosSimulatorArm64()
  // Required to generate tests tasks: https://youtrack.jetbrains.com/issue/KT-26547
  linuxX64()
  macosX64()
  macosArm64()
  mingwX64()
}

val appleTargets = listOf(
  "iosArm64",
  "iosX64",
  "iosSimulatorArm64",
  "macosX64",
  "macosArm64",
  "tvosArm64",
  "tvosX64",
  "tvosSimulatorArm64",
  "watchosArm32",
  "watchosArm64",
  "watchosX86",
  "watchosX64",
  "watchosSimulatorArm64"
)

val mingwTargets = listOf(
  "mingwX64"
)

val linuxTargets = listOf(
  "linuxX64"
)

val nativeTargets = appleTargets + linuxTargets + mingwTargets

/** Note that size_t is 32-bit on legacy watchOS versions (ie. pointers are always 32-bit). */
val unixSizet32Targets = listOf(
  "watchosArm32",
  "watchosArm64",
  "watchosX86"
)

val unixSizet64Targets = listOf(
  "iosArm64",
  "iosX64",
  "iosSimulatorArm64",
  "linuxX64",
  "macosX64",
  "macosArm64",
  "tvosArm64",
  "tvosX64",
  "tvosSimulatorArm64",
  "watchosSimulatorArm64",
  "watchosX64"
)

/**
 * Creates a source set for a directory that isn't already a built-in platform. Use this to create
 * custom shared directories like `nonJvmMain` or `unixMain`.
 */
fun NamedDomainObjectContainer<KotlinSourceSet>.createSourceSet(
  name: String,
  parent: KotlinSourceSet? = null,
  children: List<String> = listOf()
): KotlinSourceSet {
  val result = create(name)

  if (parent != null) {
    result.dependsOn(parent)
  }

  val suffix = when {
    name.endsWith("Main") -> "Main"
    name.endsWith("Test") -> "Test"
    else -> error("unexpected source set name: ${name}")
  }

  for (childTarget in children) {
    val childSourceSet = get("${childTarget}$suffix")
    childSourceSet.dependsOn(result)
  }

  return result
}

