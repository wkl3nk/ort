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

import org.apache.logging.log4j.kotlin.Logging
import org.apache.maven.model.Model
import org.apache.maven.model.Scm
import org.apache.maven.model.building.ModelBuildingResult

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.utils.parseRepoManifestPath
import org.ossreviewtoolkit.utils.common.withoutPrefix

object MavenModelExtensions : Logging

fun Model.parseAuthors(): Set<String> =
    mutableSetOf<String>().apply {
        organization?.let {
            if (!it.name.isNullOrEmpty()) add(it.name)
        }

        val developers = developers.mapNotNull { it.organization.orEmpty().ifEmpty { it.name } }
        addAll(developers)
    }

fun Model.parseLicenses(): Set<String> =
    licenses.mapNotNullTo(mutableSetOf()) { license ->
        listOfNotNull(license.name, license.url, license.comments).firstOrNull { it.isNotBlank() }
    }

// See http://maven.apache.org/pom.html#SCM.
private val SCM_REGEX = Regex("scm:(?<type>[^:@]+):(?<url>.+)")
private val USER_HOST_REGEX = Regex("scm:(?<user>[^:@]+)@(?<host>[^:]+)[:/](?<path>.+)")

fun ModelBuildingResult.parseVcsInfo(): VcsInfo {
    val scm = getOriginalScm()
    val connection = scm?.connection
    if (connection.isNullOrEmpty()) return VcsInfo.EMPTY

    val tag = scm.tag?.takeIf { it != "HEAD" }.orEmpty()

    return SCM_REGEX.matchEntire(connection)?.let { match ->
        val type = match.groups["type"]!!.value
        val url = match.groups["url"]!!.value

        handleValidScmInfo(type, url, tag)
    } ?: handleInvalidScmInfo(connection, tag)
}

private fun handleValidScmInfo(type: String, url: String, tag: String) =
    when {
        // CVS URLs usually start with ":pserver:" or ":ext:", but as ":" is also the delimiter used by the Maven SCM
        // plugin, no double ":" is used in the connection string, and we need to fix it up here.
        type == "cvs" && !url.startsWith(":") -> {
            VcsInfo(type = VcsType.CVS, url = ":$url", revision = tag)
        }

        // Maven does not officially support git-repo as an SCM, see http://maven.apache.org/scm/scms-overview.html, so
        // come up with the convention to use the "manifest" query parameter for the path to the manifest inside the
        // repository. An earlier version of this workaround expected the query string to be only the path to the
        // manifest, for backward compatibility convert such URLs to the new syntax.
        type == "git-repo" -> {
            val manifestPath = url.parseRepoManifestPath()
                ?: url.substringAfter('?').takeIf { it.isNotBlank() && it.endsWith(".xml") }
            val urlWithManifest = url.takeIf { manifestPath == null }
                ?: "${url.substringBefore('?')}?manifest=$manifestPath"

            VcsInfo(
                type = VcsType.GIT_REPO,
                url = urlWithManifest,
                revision = tag
            )
        }

        type == "svn" -> {
            val revision = tag.takeIf { it.isEmpty() } ?: "tags/$tag"
            VcsInfo(type = VcsType.SUBVERSION, url = url, revision = revision)
        }

        url.startsWith("//") -> {
            // Work around the common mistake to omit the Maven SCM provider.
            val fixedUrl = "$type:$url"

            // Try to detect the Maven SCM provider from the URL only, e.g. by looking at the host or special URL paths.
            VcsHost.parseUrl(fixedUrl).copy(revision = tag).also {
                MavenModelExtensions.logger.info { "Fixed up invalid SCM connection without a provider to $it." }
            }
        }

        else -> {
            val trimmedUrl = if (!url.startsWith("git://")) url.removePrefix("git:") else url

            VcsHost.fromUrl(trimmedUrl)?.let { host ->
                host.toVcsInfo(trimmedUrl)?.let { vcsInfo ->
                    // Fixup paths that are specified as part of the URL and contain the project name as a prefix.
                    val projectPrefix = "${host.getProject(trimmedUrl)}-"
                    vcsInfo.path.withoutPrefix(projectPrefix)?.let { path ->
                        vcsInfo.copy(path = path)
                    }
                }
            } ?: VcsInfo(type = VcsType.forName(type), url = trimmedUrl, revision = tag)
        }
    }

private fun ModelBuildingResult.handleInvalidScmInfo(connection: String, tag: String) =
    USER_HOST_REGEX.matchEntire(connection)?.let { match ->
        // Some projects omit the provider and use the SCP-like Git URL syntax, for example
        // "scm:git@github.com:facebook/facebook-android-sdk.git".
        val user = match.groups["user"]!!.value
        val host = match.groups["host"]!!.value
        val path = match.groups["path"]!!.value

        if (user == "git" || host.startsWith("git")) {
            VcsInfo(type = VcsType.GIT, url = "https://$host/$path", revision = tag)
        } else {
            VcsInfo.EMPTY
        }
    } ?: run {
        if (connection.startsWith("git://") || connection.endsWith(".git")) {
            // It is a common mistake to omit the "scm:[provider]:" prefix. Add fall-backs for nevertheless clear
            // cases.
            MavenModelExtensions.logger.info {
                "Maven SCM connection '$connection' of project '${rawModel.id}' lacks the required 'scm' prefix."
            }

            VcsInfo(type = VcsType.GIT, url = connection, revision = tag)
        } else {
            MavenModelExtensions.logger.info {
                "Ignoring Maven SCM connection '$connection' of project '${rawModel.id}' due to an unexpected " +
                        "format."
            }

            VcsInfo.EMPTY
        }
    }

fun ModelBuildingResult.getOriginalScm(): Scm? {
    val scm = effectiveModel.scm
    var parent = effectiveModel.parent

    while (parent != null) {
        val parentModel = getRawModel("${parent.groupId}:${parent.artifactId}:${parent.version}")

        parentModel.scm?.let { parentScm ->
            parentScm.connection?.let { parentConnection ->
                if (parentConnection.isNotBlank() && scm.connection.startsWith(parentConnection)) {
                    scm.connection = parentScm.connection
                }
            }

            parentScm.url?.let { parentUrl ->
                if (parentUrl.isNotBlank() && scm.url.startsWith(parentUrl)) {
                    scm.url = parentScm.url
                }
            }
        }

        parent = parentModel.parent
    }

    return scm
}
