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

package org.ossreviewtoolkit.plugins.packagemanagers.gradleinspector

import java.io.File

import org.apache.logging.log4j.kotlin.Logging

import org.gradle.wrapper.GradleUserHomeLookup

import org.ossreviewtoolkit.model.Identifier

object GradleCacheUtils : Logging {
    private val gradleCachesDir = GradleUserHomeLookup.gradleUserHome().resolve("caches")

    // The module and file cache locations have never changed so far, see
    // https://docs.gradle.org/current/userguide/dependency_resolution.html#sub:cache_copy
    private val gradleFilesCacheDir = gradleCachesDir.resolve("modules-2/files-2.1")

    /**
     * Search for the artifact matching the given [id] and [extension] in the Gradle cache and return its [File]
     * location, or null if no belonging artifact can be found.
     */
    fun findArtifact(id: Identifier, extension: String): File? {
        val artifactsRootDir = gradleFilesCacheDir.resolve("${id.namespace}/${id.name}/${id.version}")
        val artifactName = "${id.name}-${id.version}.$extension"

        val artifactFiles = artifactsRootDir.walk().filter {
            it.isFile && it.name == artifactName
        }.sortedByDescending {
            it.lastModified()
        }.toList()

        if (artifactFiles.size > 1) {
            logger.debug { "Multiple '$extension' artifacts for '${id.toCoordinates()}' found: $artifactFiles" }
        }

        // Return the most recent file, if any, as that is most likely the correct one, e.g. in case of a silent
        // update of an already published artifact.
        val artifactFile = artifactFiles.firstOrNull()

        if (artifactFile != null) {
            logger.debug { "Using '$extension' file '$artifactFile' for '${id.toCoordinates()}'." }
        } else {
            logger.debug { "No '$extension' file found for '${id.toCoordinates()}' below '$artifactsRootDir'." }
        }

        return artifactFile
    }
}
