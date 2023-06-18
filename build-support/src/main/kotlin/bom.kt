import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJsProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinJsPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * Collect all the root project's multiplatform targets and add them to the BOM.
 *
 * Only published subprojects are included.
 *
 * This supports Kotlin/Multiplatform and Kotlin/JS subprojects.
 */
fun Project.collectBomConstraints() {
  val bomConstraints: DependencyConstraintHandler = dependencies.constraints
  rootProject.subprojects {
    val subproject = this

    subproject.plugins.withId("com.vanniktech.maven.publish.base") {
      subproject.plugins.withType<KotlinMultiplatformPluginWrapper> {
        bomConstraints.api(subproject)
        subproject.extensions.getByType<KotlinMultiplatformExtension>().targets.all {
          bomConstraints.api(dependencyConstraint(this))
        }
      }

      subproject.plugins.withType<KotlinJsPluginWrapper> {
        bomConstraints.api(subproject)
        bomConstraints.api(
          dependencyConstraint(subproject.extensions.getByType<KotlinJsProjectExtension>().js())
        )
      }
    }
  }
}

private fun Project.dependencyConstraint(target: KotlinTarget) =
  "$group:$name-${target.targetName}:$version"

private fun DependencyConstraintHandler.api(constraintNotation: Any) =
  add("api", constraintNotation)
