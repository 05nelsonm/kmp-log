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

import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.name
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.log.Log
import kotlinx.coroutines.channels.Channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class FileLogBuilderUnitTest {

    @Test
    fun givenMin_whenBuild_thenDefaultIsLevelInfo() {
        assertEquals(Log.Level.Info, newBuilder().build().min)
    }

    @Test
    fun givenMinWaitOn_whenLessThanOrEqualToMin_thenDefaultIsMin() {
        val log = newBuilder()
            .min(Log.Level.Error)
            .minWaitOn(Log.Level.Info)
            .build()
        assertEquals(Log.Level.Error, log.minWaitOn)
    }

    @Test
    fun givenMinWaitOn_whenGreaterThanMin_thenIsValue() {
        val log = newBuilder()
            .min(Log.Level.Warn)
            .minWaitOn(Log.Level.Error)
            .build()
        assertEquals(Log.Level.Error, log.minWaitOn)
    }

    @Test
    fun givenMax_whenBuild_thenDefaultIsLevelFatal() {
        assertEquals(Log.Level.Fatal, newBuilder().build().max)
    }

    @Test
    fun givenBufferCapacity_whenMinWaitOnEqualsMin_thenIsChannelUNLIMITED() {
        assertEquals(Channel.UNLIMITED, newBuilder().build().bufferCapacity)
    }

    @Test
    fun givenBufferCapacity_whenMinWaitOnGreaterThanMin_thenIsCapacity() {
        val log = newBuilder()
            .minWaitOn(Log.Level.Fatal)
            .bufferCapacity(2)
            .build()
        assertEquals(2, log.bufferCapacity)
    }

    @Test
    fun givenBufferCapacity_whenMinWaitOnGreaterThanMinButCapacityLessThanChannelRENDEZVOUS_thenIsChannelRENDEZVOUS() {
        val log = newBuilder()
            .minWaitOn(Log.Level.Fatal)
            .bufferCapacity(-1)
            .build()
        assertEquals(Channel.RENDEZVOUS, log.bufferCapacity)
    }

    @Test
    fun givenBuild_whenFileLogUid_thenIsAsExpected() {
        val fileLog = newBuilder().build()
        val prefix = "io.matthewnelson.kmp.log.file.FileLog-"
        assertTrue(fileLog.uid.startsWith(prefix))
        assertEquals(prefix.length + 24, fileLog.uid.length)
        assertEquals(24, fileLog.logFiles0Hash.length)
        assertTrue(fileLog.uid.endsWith(fileLog.logFiles0Hash))
    }

    @Test
    fun givenLogDirectory_whenEmpty_thenThrowsIllegalArgumentException() {
        try {
            newBuilder("")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("logDirectory cannot be empty", e.message)
        }
    }

    @Test
    fun givenLogDirectory_whenContainsNullCharacter_thenThrowsIllegalArgumentException() {
        try {
            newBuilder(SysTempDir.path + SysDirSep + '\u0000')
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("logDirectory cannot contain null character '\\u0000'", e.message)
        }
    }

    @Test
    fun givenFileName_whenEmpty_thenThrowsIllegalArgumentException() {
        try {
            newBuilder().fileName("")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileName cannot be empty", e.message)
        }
    }

    @Test
    fun givenFileName_whenGreaterThan64Characters_thenThrowsIllegalArgumentException() {
        try {
            val name = buildString {
                repeat(65) { append("a") }
            }
            newBuilder().fileName(name)
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileName cannot exceed 64 characters", e.message)
        }
    }

    @Test
    fun givenFileName_when1Character_thenDotLockFileNameIs7CharacterInLength() {
        // Required minimum for File.deleteOrMoveToRandomIfNonEmptyDirectory
        assertEquals(7, newBuilder().fileName("a").build().dotLockFile.name.length)
    }

    @Test
    fun givenFileName_whenEndsWithDot_thenThrowsIllegalArgumentException() {
        try {
            newBuilder().fileName("something.")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileName cannot end with '.'", e.message)
        }
    }

    @Test
    fun givenFileName_whenContainsSlash_thenThrowsIllegalArgumentException() {
        try {
            newBuilder().fileName("some/thing")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileName cannot contain '/'", e.message)
        }
        try {
            newBuilder().fileName("some\\thing")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileName cannot contain '\\'", e.message)
        }
    }

    @Test
    fun givenFileName_whenContainsWhitespace_thenThrowsIllegalArgumentException() {
        try {
            newBuilder().fileName("something ")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileName cannot contain whitespace", e.message)
        }
    }

    @Test
    fun givenFileName_whenContainsNullCharacter_thenThrowsIllegalArgumentException() {
        try {
            newBuilder().fileName("something" + '\u0000')
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileName cannot contain null character '\\u0000'", e.message)
        }
    }

    @Test
    fun givenFileExtension_whenGreaterThan8Characters_thenThrowsIllegalArgumentException() {
        try {
            val name = buildString {
                repeat(9) { append("a") }
            }
            newBuilder().fileExtension(name)
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileExtension cannot exceed 8 characters", e.message)
        }
    }

    @Test
    fun givenFileExtension_whenContainsDot_thenThrowsIllegalArgumentException() {
        try {
            newBuilder().fileExtension("abc.def")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileExtension cannot contain '.'", e.message)
        }
    }

    @Test
    fun givenFileExtension_whenContainsSlash_thenBuildThrowsIllegalArgumentException() {
        try {
            newBuilder().fileExtension("abc/def")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileExtension cannot contain '/'", e.message)
        }
        try {
            newBuilder().fileExtension("abc\\def")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileExtension cannot contain '\\'", e.message)
        }
    }

    @Test
    fun givenFileExtension_whenContainsWhitespace_thenBuildThrowsIllegalArgumentException() {
        try {
            newBuilder().fileExtension("abc def")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileExtension cannot contain whitespace", e.message)
        }
    }

    @Test
    fun givenFileExtension_whenContainsNullCharacter_thenBuildThrowsIllegalArgumentException() {
        try {
            newBuilder().fileExtension("abcdef" + '\u0000')
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("fileExtension cannot contain null character '\\u0000'", e.message)
        }
    }

    @Test
    fun givenMaxLogFiles_whenLessThan2_thenUses2() {
        assertEquals(2, newBuilder().maxLogFiles(0).build().logFiles.size)
    }

    @Test
    fun givenMaxLogFiles_whenLogFiles_thenSizeEquals() {
        val expected = 10
        assertEquals(expected, newBuilder().maxLogFiles(expected.toByte()).build().logFiles.size)
    }

    @Test
    fun givenMaxLogFileSize_whenLessThan50Kb_thenUses50Kb() {
        assertEquals(50 * 1024L, newBuilder().maxLogFileSize(5).build().maxLogFileSize)
    }

    @Test
    fun givenYieldOn_whenLessThan1_thenUses1() {
        assertEquals(1, newBuilder().yieldOn(0).build().yieldOn)
    }

    @Test
    fun givenYieldOn_whenGreaterThan10_thenUses10() {
        assertEquals(10, newBuilder().yieldOn(11).build().yieldOn)
    }

    @Test
    fun givenLogFiles_whenCastAsMutable_thenThrowsClassCastException() {
        assertFailsWith<ClassCastException> {
            newBuilder().build().logFiles as MutableList<String>
        }
    }

    @Test
    fun givenLogFiles_whenIndex0_thenDoesNotHaveNumberSuffix() {
        val file = newBuilder().fileExtension("txt").build().logFiles.first()
        assertTrue(file.endsWith(".txt"))
    }

    @Test
    fun givenLogFiles_whenIndexGreaterThan0_thenHasNumberSuffix() {
        val files = newBuilder().maxLogFiles(5).build().logFiles
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
    fun givenBlacklistDomain_whenCastAsMutable_thenThrowsClassCastException() {
        val domains = newBuilder().blacklistDomain("kmp.log").build().blacklistDomain
        assertEquals(1, domains.size)
        assertFailsWith<ClassCastException> { domains as MutableSet<String> }
    }

    @Test
    fun givenWhitelistDomain_whenCastAsMutable_thenThrowsClassCastException() {
        val domains = newBuilder().whitelistDomain("kmp.log").build().whitelistDomain
        assertEquals(1, domains.size)
        assertFailsWith<ClassCastException> { domains as MutableSet<String> }
    }

    @Test
    fun givenBuilder_whenWhitelistDomain_thenChecksValidity() {
        val b = newBuilder()
        arrayOf<FileLog.Builder.() -> Unit>(
            { whitelistDomain("") },
            { whitelistDomain("", "") },
            { whitelistDomain(listOf("")) },
        ).forEach { fn ->
            try {
                fn(b)
                fail()
            } catch (e: IllegalArgumentException) {
                assertEquals(true, e.message?.startsWith("domain.length[0] < min["))
            }
        }
    }

    @Test
    fun givenBuilder_whenWhitelistTags_thenChecksValidity() {
        val b = newBuilder()
        arrayOf<FileLog.Builder.() -> Unit>(
            { whitelistTag("") },
            { whitelistTag("", "") },
            { whitelistTag(listOf("")) },
        ).forEach { fn ->
            try {
                fn(b)
                fail()
            } catch (e: IllegalArgumentException) {
                assertEquals("tag cannot be empty", e.message)
            }
        }
    }

    @Test
    fun givenBuilder_whenWhitelistDomainReset_thenResetsConfigured() {
        val b = newBuilder().whitelistDomain("kmp.log")
        assertEquals(1, b.build().whitelistDomain.size)

        b.whitelistDomainReset()
        assertEquals(0, b.build().whitelistDomain.size)

        // Ensure current setting is false
        b.whitelistDomain("kmp.log").whitelistDomainNull(false)
        assertFalse(b.build().whitelistDomainNull)

        // Ensure reset also resets whitelistDomainNull to true
        b.whitelistDomainReset().whitelistDomain("kmp.log")
        assertTrue(b.build().whitelistDomainNull)
    }

    @Test
    fun givenBuilder_whenWhitelistTagReset_thenResetsConfigured() {
        val b = newBuilder().whitelistTag("something")
        assertEquals(1, b.build().whitelistTag.size)

        b.whitelistTagReset()
        assertEquals(0, b.build().whitelistTag.size)
    }

    @Test
    fun givenWhitelistTags_whenCastAsMutable_thenThrowsClassCastException() {
        val tags = newBuilder().whitelistTag("Tag").build().whitelistTag
        assertEquals(1, tags.size)
        assertFailsWith<ClassCastException> { tags as MutableSet<String> }
    }

    private fun newBuilder(dir: String = SysTempDir.path) = FileLog.Builder(dir)
}
