/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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

package org.ossreviewtoolkit.evaluator

import java.io.File

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.utils.common.FileMatcher

/**
 * An [OrtResultRule] which allows downloading the project's source code if needed.
 */
open class ProjectSourceRule(
    ruleSet: RuleSet,
    name: String,
    projectSourceResolver: SourceTreeResolver = ruleSet.ortResult.createResolver()
) : OrtResultRule(ruleSet, name) {
    /**
     * The directory containing the source code of the project. Accessing the property for the first time triggers a
     * clone and may take a while.
     */
    val projectSourcesDir: File by lazy { projectSourceResolver.rootDir }

    /**
     * Return all files from the project's source tree which match any of the provided [glob expressions][patterns].
     */
    fun projectSourceFindFiles(vararg patterns: String): List<File> =
        projectSourcesDir.walkBottomUp().filterTo(mutableListOf()) {
            it.isFile && FileMatcher.match(patterns.toList(), it.relativeTo(projectSourcesDir).path)
        }

    /**
     * A [RuleMatcher] that checks whether the project's source tree contains at least one file matching any of the
     * provided [glob expressions][patterns].
     */
    fun projectSourceHasFile(vararg patterns: String): RuleMatcher =
        object : RuleMatcher {
            override val description = "projectSourceHasFile('${patterns.joinToString()}')"
            override fun matches(): Boolean = projectSourceFindFiles(*patterns).isNotEmpty()
        }
}

private fun OrtResult.createResolver() =
    SourceTreeResolver.forRemoteRepository(repository.vcsProcessed)
