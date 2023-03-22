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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.utils.common.Os

object MavenModelUtils {
    private val m2 = Os.env["M2_HOME"]?.let { File(it) } ?: Os.userHomeDirectory.resolve(".m2")

    /**
     * Search for the artifact matching the given [id] and [extension] in the local Maven repository and return its
     * [File] location, or null if no belonging artifact can be found.
     */
    fun findArtifact(id: Identifier, extension: String): File? {
        val group = id.namespace.replace('.', '/')
        return m2.resolve("repository/$group/${id.name}/${id.version}/${id.name}-${id.version}.$extension")
            .takeIf { it.isFile }
    }
}
