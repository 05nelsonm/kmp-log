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
package io.matthewnelson.kmp.log

import io.matthewnelson.kmp.log.Log.Root
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class LoggerUnitTest {

    private companion object {
        private val LOG = Log.Logger.of(domain = "kmp-log:log", tag = "LoggerUnitTest")
    }

    @Test
    fun givenLoggerOf_whenNoDomainSameTag_thenSameInstanceIsReturned() {
        val tag = "NoDomainSameTag"
        val before = Log.Logger.size()
        val logger1 = Log.Logger.of(tag)
        val logger2 = Log.Logger.of(tag)
        assertEquals(1, Log.Logger.size() - before)
        assertEquals(logger1, logger2)
    }

    @Test
    fun givenLoggerOf_whenSameDomainSameTag_thenSameInstanceIsReturned() {
        val domain = "same.domain.same.tag"
        val tag = "SameDomainSameTag"
        val before = Log.Logger.size()
        val logger1 = Log.Logger.of(tag, domain)
        val logger2 = Log.Logger.of(tag, domain)
        assertEquals(1, Log.Logger.size() - before)
        assertEquals(logger1, logger2)
    }

    @Test
    fun givenLoggerOf_whenSameDomainDifferentTag_thenDifferentInstanceIsReturned() {
        val domain = "same.domain.diff.tag"
        val tag = "SameDomainDiffTag"
        val before = Log.Logger.size()
        val logger1 = Log.Logger.of(tag + "A", domain)
        val logger2 = Log.Logger.of(tag + "B", domain)
        assertEquals(2, Log.Logger.size() - before)
        assertNotEquals(logger1, logger2)
    }

    @Test
    fun givenLoggerOf_whenDifferentDomainAndSameTag_thenDifferentInstanceIsReturned() {
        val domain = "diff.domain.same.tag"
        val tag = "DiffDomainSameTag"
        val before = Log.Logger.size()
        val logger1 = Log.Logger.of(tag, domain + "a")
        val logger2 = Log.Logger.of(tag, domain + "b")
        assertEquals(2, Log.Logger.size() - before)
        assertNotEquals(logger1, logger2)
    }

    @Test
    fun givenCheckDomain_whenNull_thenReturnsNull() {
        assertNull(Log.Logger.checkDomain(domain = null))
    }

    @Test
    fun givenCheckDomain_whenLessThan3Characters_thenThrowsIllegalArgumentException() {
        try {
            Log.Logger.checkDomain("ab")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message?.contains("< min[3]"))
        }
    }

    @Test
    fun givenCheckDomain_whenGreaterThan32Characters_thenThrowsIllegalArgumentException() {
        var domain = "a.b"
        repeat(32 - domain.length) { domain += "a" }
        assertEquals(domain, Log.Logger.checkDomain(domain))
        domain += "b"
        try {
            Log.Logger.checkDomain(domain)
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message?.contains("> max[32]"))
        }
    }

    @Test
    fun givenCheckDomain_whenUppercase_thenThrowsIllegalArgumentException() {
        val domain = "a.b"
        assertEquals(domain, Log.Logger.checkDomain(domain))
        try {
            Log.Logger.checkDomain(domain.uppercase())
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message?.contains("Allowable characters are [a-z]"))
        }
    }

    @Test
    fun givenCheckDomain_whenDoesNotContainASeparator_thenThrowsIllegalArgumentException() {
        try {
            Log.Logger.checkDomain("abc")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message?.contains("at least 1 separator character"))
        }
    }

    @Test
    fun givenCheckDomain_whenStartsWithASeparator_thenThrowsIllegalArgumentException() {
        try {
            Log.Logger.checkDomain(".ab")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("domain must start with character [a-z] or [0-9]", e.message)
        }
    }

    @Test
    fun givenCheckDomain_whenEndsWithASeparator_thenThrowsIllegalArgumentException() {
        try {
            Log.Logger.checkDomain("ab.")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("domain must end with character [a-z] or [0-9]", e.message)
        }
    }

    @Test
    fun givenCheckDomain_whenDoubleSeparator_thenThrowsIllegalArgumentException() {
        try {
            Log.Logger.checkDomain("a..b")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message?.contains("cannot precede or follow another separator character"))
        }
    }

    @Test
    fun givenCheckTag_whenNull_thenThrowsNullPointerException() {
        assertFailsWith<NullPointerException> { Log.Logger.checkTag(tag = null) }
    }

    @Test
    fun givenCheckTag_whenEmpty_thenThrowsIllegalArgument() {
        try {
            Log.Logger.checkTag(tag = "")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("tag cannot be empty", e.message)
        }
    }

    @Test
    fun givenCheckTag_whenGreaterThan128Characters_thenThrowsIllegalArgumentException() {
        var tag = ""
        repeat(128) { tag += "A" }
        assertEquals(tag, Log.Logger.checkTag(tag))
        tag += "b"
        try {
            Log.Logger.checkTag(tag)
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message?.contains("> max[128]"))
        }
    }

    @Test
    fun givenCheckTag_whenContainsWhitespace_thenThrowsIllegalArgument() {
        arrayOf(' ', '\n', '\r', '\t').forEach { char ->
            val tag = "Some${char}Tag"
            try {
                Log.Logger.checkTag(tag = tag)
                fail()
            } catch (e: IllegalArgumentException) {
                assertEquals("tag cannot contain whitespace", e.message)
            }
        }
    }

    @Test
    fun givenLogger_whenNoLogInstanceInstalled_thenIsLoggableAlwaysReturnsFalse() {
        try {
            Root.uninstallOrThrow(Log.AbortHandler.UID)
            Log.Level.entries.forEach { level ->
                assertFalse(LOG.isLoggable(level))
            }
        } finally {
            Root.installOrThrow(Log.AbortHandler)
        }
    }

    @Test
    fun givenLogger_whenLogVerbose_thenVerboseLevelIsSentToLogInstance() {
        val log = TestLog(uid = "Verbose", min = Log.Level.Verbose)
        try {
            Root.installOrThrow(log)
            LOG.v("1")
            LOG.v(Throwable("2"))
            LOG.v(null, Throwable("3"))
            LOG.v("4", Throwable("4"))
            LOG.v { "5" }
            LOG.v(Throwable("6")) { "6" }
            val logs = log.logs
            assertNotNull(logs)
            assertEquals(6, logs.size)
            logs.forEach { logItem ->
                assertEquals(Log.Level.Verbose, logItem.level)
            }
        } finally {
            Root.uninstallOrThrow(log)
        }
    }

    @Test
    fun givenLogger_whenLogDebug_thenDebugLevelIsSentToLogInstance() {
        val log = TestLog(uid = "Debug", min = Log.Level.Debug)
        try {
            Root.installOrThrow(log)
            LOG.d("1")
            LOG.d(Throwable("2"))
            LOG.d(null, Throwable("3"))
            LOG.d("4", Throwable("4"))
            LOG.d { "5" }
            LOG.d(Throwable("6")) { "6" }
            val logs = log.logs
            assertNotNull(logs)
            assertEquals(6, logs.size)
            logs.forEach { logItem ->
                assertEquals(Log.Level.Debug, logItem.level)
            }
        } finally {
            Root.uninstallOrThrow(log)
        }
    }

    @Test
    fun givenLogger_whenLogInfo_thenInfoLevelIsSentToLogInstance() {
        val log = TestLog(uid = "Info", min = Log.Level.Info)
        try {
            Root.installOrThrow(log)
            LOG.i("1")
            LOG.i(Throwable("2"))
            LOG.i(null, Throwable("3"))
            LOG.i("4", Throwable("4"))
            LOG.i { "5" }
            LOG.i(Throwable("6")) { "6" }
            val logs = log.logs
            assertNotNull(logs)
            assertEquals(6, logs.size)
            logs.forEach { logItem ->
                assertEquals(Log.Level.Info, logItem.level)
            }
        } finally {
            Root.uninstallOrThrow(log)
        }
    }

    @Test
    fun givenLogger_whenLogWarn_thenWarnLevelIsSentToLogInstance() {
        val log = TestLog(uid = "Warn", min = Log.Level.Warn)
        try {
            Root.installOrThrow(log)
            LOG.w("1")
            LOG.w(Throwable("2"))
            LOG.w(null, Throwable("3"))
            LOG.w("4", Throwable("4"))
            LOG.w { "5" }
            LOG.w(Throwable("6")) { "6" }
            val logs = log.logs
            assertNotNull(logs)
            assertEquals(6, logs.size)
            logs.forEach { logItem ->
                assertEquals(Log.Level.Warn, logItem.level)
            }
        } finally {
            Root.uninstallOrThrow(log)
        }
    }

    @Test
    fun givenLogger_whenLogError_thenErrorLevelIsSentToLogInstance() {
        val log = TestLog(uid = "Error", min = Log.Level.Error)
        try {
            Root.installOrThrow(log)
            LOG.e("1")
            LOG.e(Throwable("2"))
            LOG.e(null, Throwable("3"))
            LOG.e("4", Throwable("4"))
            LOG.e { "5" }
            LOG.e(Throwable("6")) { "6" }
            val logs = log.logs
            assertNotNull(logs)
            assertEquals(6, logs.size)
            logs.forEach { logItem ->
                assertEquals(Log.Level.Error, logItem.level)
            }
        } finally {
            Root.uninstallOrThrow(log)
        }
    }

    @Test
    fun givenLogger_whenLogFatal_thenFatalLevelIsSentToLogInstance() {
        val log = TestLog(uid = "Fatal", min = Log.Level.Fatal)
        try {
            Root.installOrThrow(log)
            Root.uninstallOrThrow(Log.AbortHandler.UID)
            LOG.wtf("1")
            LOG.wtf(Throwable("2"))
            LOG.wtf(null, Throwable("3"))
            LOG.wtf("4", Throwable("4"))
            LOG.wtf { "5" }
            LOG.wtf(Throwable("6")) { "6" }
            val logs = log.logs
            assertNotNull(logs)
            assertEquals(6, logs.size)
            logs.forEach { logItem ->
                assertEquals(Log.Level.Fatal, logItem.level)
            }
        } finally {
            Root.uninstallOrThrow(log)
            Root.installOrThrow(Log.AbortHandler)
        }
    }

    @Test
    fun givenLogger_whenMsgIsNullAndTIsNull_thenIsNotLogged() {
        try {
            val log = TestLog(uid = "msg_null")
            Root.installOrThrow(log)
            assertEquals(0, LOG.wtf(null, null))
            assertEquals(0, log.logs?.size)
        } finally {
            Root.uninstallOrThrow("msg_null")
        }
    }

    @Test
    fun givenLogger_whenMsgIsEmptyAndTIsNull_thenIsNotLogged() {
        try {
            val log = TestLog(uid = "msg_empty")
            Root.installOrThrow(log)
            assertEquals(0, LOG.wtf("", null))
            assertEquals(0, log.logs?.size)
        } finally {
            Root.uninstallOrThrow("msg_empty")
        }
    }
}
