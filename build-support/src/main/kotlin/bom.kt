/*
 * Copyright (C) 2023 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinJsPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMetadataTarget
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget

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
      subproject.plugins.withType<KotlinAndroidPluginWrapper> {
        bomConstraints.api(subproject)
      }

      subproject.plugins.withType<KotlinJsPluginWrapper> {
        bomConstraints.api(subproject)
      }

      subproject.plugins.withType<KotlinMultiplatformPluginWrapper> {
        subproject.extensions.getByType<KotlinMultiplatformExtension>().targets.all {
          bomConstraints.api(dependencyConstraint(this))
        }
      }
    }
  }
}

/** Returns a string like "com.squareup.okio:okio-iosarm64:3.4.0" for this target. */
private fun Project.dependencyConstraint(target: KotlinTarget): String {
  val artifactId = when (target) {
    is KotlinMetadataTarget -> name
    is KotlinJsTarget -> "$name-js"
    else -> "$name-${target.targetName.toLowerCase(Locale.ROOT)}"
  }
  return "$group:$artifactId:$version"
}

private fun DependencyConstraintHandler.api(constraintNotation: Any) =
  add("api", constraintNotation)
