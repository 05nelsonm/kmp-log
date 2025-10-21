/*
 * Copyright (c) 2025 Matthew Nelson
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
 **/
package io.matthewnelson.kmp.log.file

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.log.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileLogBuilderUnitTest {

    @Test
    fun givenMin_whenBuild_thenDefaultIsLevelInfo() {
        assertEquals(Log.Level.Info, newBuilder().build().min)
    }

    @Test
    fun givenMax_whenBuild_thenDefaultIsLevelFatal() {
        assertEquals(Log.Level.Fatal, newBuilder().build().max)
    }

    @Test
    fun givenFileName_whenEmpty_thenBuildThrowsIllegalArgumentException() {
        try {
            newBuilder().fileName("").build()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileName cannot be empty", e.message)
        }
    }

    @Test
    fun givenFileName_whenGreaterThan64Characters_thenBuildThrowsIllegalArgumentException() {
        try {
            val name = buildString {
                repeat(65) { append("a") }
            }
            newBuilder().fileName(name).build()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileName cannot exceed 64 characters", e.message)
        }
    }

    @Test
    fun givenFileName_whenEndsWithDot_thenBuildThrowsIllegalArgumentException() {
        try {
            newBuilder().fileName("something.").build()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileName cannot end with '.'", e.message)
        }
    }

    @Test
    fun givenFileName_whenContainsWhitespace_thenBuildThrowsIllegalArgumentException() {
        try {
            newBuilder().fileName("something ").build()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileName cannot contain whitespace", e.message)
        }
    }

    @Test
    fun givenFileExtension_whenGreaterThan8Characters_thenBuildThrowsIllegalArgumentException() {
        try {
            val name = buildString {
                repeat(9) { append("a") }
            }
            newBuilder().fileExtension(name).build()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileExtension cannot exceed 8 characters", e.message)
        }
    }

    @Test
    fun givenFileExtension_whenContainsDot_thenBuildThrowsIllegalArgumentException() {
        try {
            newBuilder().fileExtension("abc.def").build()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileExtension cannot contain '.'", e.message)
        }
    }

    @Test
    fun givenFileExtension_whenContainsWhitespace_thenBuildThrowsIllegalArgumentException() {
        try {
            newBuilder().fileExtension("abc def").build()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileExtension cannot contain whitespace", e.message)
        }
    }

    @Test
    fun givenMaxLogs_whenLessThan2_thenUses2() {
        assertEquals(2, newBuilder().maxLogs(0).build().logFiles.size)
    }

    @Test
    fun givenMaxLogs_whenLogFiles_thenSizeEquals() {
        val expected = 10
        assertEquals(expected, newBuilder().maxLogs(expected.toByte()).build().logFiles.size)
    }

    @Test
    fun givenMaxLogSize_whenLessThan50Kb_thenUses50Kb() {
        assertEquals(50 * 1024L, newBuilder().maxLogSize(5).build().maxLogSize)
    }

    @Test
    fun givenLogFiles_whenCastAsMutable_thenThrowsClassCastException() {
        assertFailsWith<ClassCastException> {
            newBuilder().build().logFiles as MutableSet<String>
        }
    }

    @Test
    fun givenLogFiles_whenIndex0_thenDoesNotHaveNumberSuffix() {
        val file = newBuilder().fileExtension("txt").build().logFiles.first()
        assertTrue(file.endsWith(".txt"))
    }

    @Test
    fun givenLogFiles_whenIndexGreaterThan0_thenHasNumberSuffix() {
        val files = newBuilder().maxLogs(5).build().logFiles
        assertEquals(5, files.size)
        for (i in 1 until 5) {
            val end = files.elementAt(i).substringAfterLast('.', "_")
            assertEquals(i, end.toInt())
        }
    }

    @Test
    fun givenLogFiles_whenConstructed_thenAllStartWithLogDirectoryPath() {
        val log = newBuilder().build()
        for (file in log.logFiles) {
            assertTrue(file.startsWith(log.logDirectory))
        }
    }

    @Test
    fun givenWhitelistDomains_whenCastAsMutable_thenThrowsClassCastException() {
        val domains = newBuilder().whitelistDomain("kmp.log").build().whitelistDomains
        assertEquals(1, domains.size)
        assertFailsWith<ClassCastException> { domains as MutableSet<String> }
    }

    @Test
    fun givenWhitelistDomains_whenBuild_thenChecksValidity() {
        val b = newBuilder().whitelistDomain("")
        try {
            b.build()
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message?.startsWith("domain.length[0] < min["))
        }
    }

    @Test
    fun givenWhitelistTags_whenCastAsMutable_thenThrowsClassCastException() {
        val tags = newBuilder().whitelistTag("Tag").build().whitelistTags
        assertEquals(1, tags.size)
        assertFailsWith<ClassCastException> { tags as MutableSet<String> }
    }

    @Test
    fun givenWhitelistTags_whenBuild_thenChecksValidity() {
        val b = newBuilder().whitelistTag("")
        try {
            b.build()
        } catch (e: IllegalArgumentException) {
            assertEquals("tag cannot be empty", e.message)
        }
    }

    private fun newBuilder(dir: File = SysTempDir) = FileLog.Builder(dir.path)
}
