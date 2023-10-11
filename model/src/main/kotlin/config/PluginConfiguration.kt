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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.annotation.JsonIgnore

import org.ossreviewtoolkit.utils.common.ConfigurablePluginFactory
import org.ossreviewtoolkit.utils.common.Options

/**
 * The configuration of [configurable plugins][ConfigurablePluginFactory].
 */
data class PluginConfiguration(
    /**
     * The configuration options of the plugin. See the specific implementation for available configuration options.
     */
    val options: Options = emptyMap(),

    /**
     * The configuration secrets of the plugin. See the specific implementation for available secret options.
     *
     * This property is not serialized to ensure that secrets do not appear in serialized output.
     */
    @JsonIgnore
    val secrets: Options = emptyMap()
) {
    /**
     * Return a string representation of the object that does not contain the [secrets].
     */
    override fun toString() = "${this::class.simpleName}(options=$options)"
}
