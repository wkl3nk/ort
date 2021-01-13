/*
 * Copyright (C) 2020 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

import java.io.File

import kotlin.io.path.createTempFile

import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.model.readValue

class ScannerConfigurationTest : WordSpec({
    "ScannerConfiguration" should {
        "support a serialization round-trip via an ObjectMapper" {
            val refConfigFile = File("src/test/assets/reference.conf")
            val refConfig = OrtConfiguration.load(configFile = refConfigFile)
            val expectedConfig = spyk(refConfig.scanner!!, "oink") {
                every { archive } returns spyk(name = "archive") {
                    every { storage } returns spyk(name = "storage") {
                        every { localFileStorage } returns spyk(name = "localFileStorage") {
                            // Jackson serializes Files as absolute (!) paths, so mock it for testing.
                            every { directory } returns File("/mocked/absolute/path")
                        }
                    }
                }
            }

            val file = createTempFile(suffix = ".yml").toFile().apply { deleteOnExit() }
            file.mapper().writeValue(file, expectedConfig)
            val actualConfig = file.readValue<ScannerConfiguration>()

            actualConfig shouldBe expectedConfig

            // Note: loadedConfig cannot be directly compared to the original one, as there have been some changes:
            // Relative paths have been normalized, passwords do not get serialized, etc.
            actualConfig.storageReaders shouldBe refConfig.scanner?.storageReaders
            actualConfig.storageWriters shouldBe refConfig.scanner?.storageWriters
            actualConfig.archive?.storage?.httpFileStorage.shouldBeNull()

            val loadedStorages = actualConfig.storages.orEmpty()
            val orgStorages = refConfig.scanner?.storages.orEmpty()
            loadedStorages.keys shouldContainExactly orgStorages.keys
            loadedStorages.forEach { e ->
                val orgStorage = orgStorages[e.key] ?: this
                e.value::class shouldBe orgStorage::class
            }
        }
    }
})
