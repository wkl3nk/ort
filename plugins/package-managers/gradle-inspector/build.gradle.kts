/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers

@Suppress("DSL_SCOPE_VIOLATION") // See https://youtrack.jetbrains.com/issue/KTIJ-19369.
plugins {
    // Apply core plugins.
    `java-library`

    // Apply third-party plugins.
    alias(libs.plugins.ideaExt)
}

repositories {
    exclusiveContent {
        forRepository {
            maven("https://repo.gradle.org/gradle/libs-releases/")
        }

        filter {
            includeGroup("org.gradle")
        }
    }
}

dependencies {
    api(project(":analyzer"))

    implementation(project(":downloader"))
    implementation(project(":plugins:package-managers:gradle-model"))

    implementation("org.gradle:gradle-tooling-api:${gradle.gradleVersion}")

    funTestImplementation(testFixtures(project(":analyzer")))
}

val processResources = tasks.named<Copy>("processResources").configure {
    val gradlePluginProject = project(":plugins:package-managers:gradle-plugin")
    val gradlePluginJarTask = gradlePluginProject.tasks.named<Jar>("fatJar")
    val gradlePluginJarFile = gradlePluginJarTask.get().outputs.files.singleFile

    // As the Copy-task simply skips non-existing files, add explicit dependencies on the Jar-tasks.
    dependsOn(gradlePluginJarTask)

    // Bundle the plugin JAR as a resource so the inspector can copy it at runtime to the init script's classpath.
    from(gradlePluginJarFile)

    // Ensure a constant file name without a version suffix.
    rename(gradlePluginJarFile.name, "gradle-plugin.jar")
}

// Work around https://youtrack.jetbrains.com/issue/IDEA-173367.
rootProject.idea.project.settings.taskTriggers.beforeBuild(processResources)
