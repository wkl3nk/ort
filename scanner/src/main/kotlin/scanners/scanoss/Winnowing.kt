/*
 * Copyright (C) 2022 The ORT Project Authors
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

package org.ossreviewtoolkit.scanner.scanners.scanoss

import java.io.File
import java.security.MessageDigest
import java.util.Locale

private val SPACE_CHAR: Byte = 0x20
private val TEXT_CONTROL_CHARS = byteArrayOf(
    0x09 /* horizontal tab */,
    0x0a /* line feed */,
    0x0d /* carriage return */
)

private fun normalizeLine(line: String): List<Char> =
    line.mapNotNull { char -> char.takeIf { it.isLetterOrDigit() }?.lowercaseChar() }

class Winnowing(
    private val digest: MessageDigest = MessageDigest.getInstance("MD5"),
    private val gramSize: Int = 30,
    private val windowSize: Int = 64
) {
    data class HashResult(val hashValue: String, val isBinary: Boolean)

    /**
     * Calculate the hash of a [file] and return whether it is a binary file or not.
     */
    fun calculateHash(file: File): HashResult {
        var isBinary: Boolean? = null
        var nonPrintableChars = 0

        val digest = file.inputStream().use { inputStream ->
            // 4 MiB has been chosen rather arbitrarily, hoping that it provides good performance while not consuming a
            // lot of memory at the same time, also considering that this function could potentially be run on multiple
            // threads in parallel.
            val buffer = ByteArray(4 * 1024 * 1024)

            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                if (isBinary == null) {
                    // Look at the first 1024 bytes of content to determine whether the file is binary or not.
                    val peekLength = length.coerceAtMost(1024)
                    repeat(peekLength) {
                        val byte = buffer[it]
                        if (byte < SPACE_CHAR && byte !in TEXT_CONTROL_CHARS) ++nonPrintableChars
                    }

                    isBinary = (nonPrintableChars * 100 / peekLength) > 5
                }
                digest.update(buffer, 0, length)
            }

            digest.digest()
        }

        return HashResult(
            digest.joinToString("") { String.format(Locale.ROOT, "%02x", it) },
            isBinary == true
        )
    }

    fun calculateFingerprint(file: File): String =
        buildString {
            val hashResult = calculateHash(file)
            appendLine("file=${hashResult.hashValue},${file.length()},${file.name}")
            if (hashResult.isBinary) return@buildString

            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val normalizedLine = normalizeLine(line)
                    if (normalizedLine.size < gramSize) return@forEachIndexed

                    //val windows = normalizedLine.windowed(windowSize, gramSize)
                    //windows.forEach { _ ->
                    appendLine("${index + 1},")
                    //}
                }
            }
        }
}
