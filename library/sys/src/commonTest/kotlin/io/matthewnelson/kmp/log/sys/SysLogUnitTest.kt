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
package io.matthewnelson.kmp.log.sys

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.Log.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SysLogUnitTest {

    private companion object {
        private val LOG = Log.Logger.of(tag = "SysLogUnitTest")
    }

    @Test
    fun givenSysLog_whenUid_thenIsAsExpected() {
        val expected = "io.matthewnelson.kmp.log.sys.SysLog"
        assertEquals(expected, SysLog.UID)
        assertEquals(expected, SysLog.uid)
    }

    @Test
    fun givenSysLog_whenDefaultConstructor_thenMinIsLevelDebug() {
        assertEquals(Level.Debug, SysLog.min)
    }

    @Test
    fun givenSysLog_whenOfWithNonDefaultValue_thenReturnsNewInstance() {
        val actual = SysLog.of(min = Level.Info)
        assertNotEquals(SysLog.Default, actual)
    }

    @Test
    fun givenSysLog_whenDefault_thenToStringStartsWithSysLogDotDefault() {
        assertTrue(SysLog.toString().startsWith("SysLog.Default["))
    }

    @Test
    fun givenIsInstalled_whenNotInstalled_thenReturnsFalse() {
        assertFalse(SysLog.isInstalled)
    }

    @Test
    fun givenIsInstalled_whenIsInstalled_thenReturnsTrue() {
        try {
            Log.installOrThrow(SysLog)
            assertTrue(SysLog.isInstalled)
        } finally {
            Log.uninstall(SysLog)
        }
    }

    @Test
    fun givenSysLog_whenLogThings_thenReturnsTrue() {
        Log.uninstallOrThrow(Log.AbortHandler.UID)
        Log.installOrThrow(SysLog.of(Level.Verbose))
        try {
            assertEquals(1, LOG.v { "TEST VERBOSE" }, "v")
            assertEquals(1, LOG.d { "TEST DEBUG" }, "d")
            assertEquals(1, LOG.i { "TEST INFO" }, "i")
            assertEquals(1, LOG.w { "TEST WARN" }, "w")
            assertEquals(1, LOG.e(IllegalStateException("log error")) { "TEST ERROR" }, "e")
            assertEquals(1, LOG.wtf(UnsupportedOperationException("log fatal")) { "TEST FATAL" }, "wtf")
        } finally {
            Log.install(Log.AbortHandler)
            Log.uninstallOrThrow(SysLog.UID)
        }
    }

    @Test
    fun givenSysLog_whenLogIsAllWhitespace_thenDoesNotLog() {
        Log.install(SysLog)
        try {
            assertEquals(true, LOG.isLoggable(Level.Error))
            assertEquals(0, LOG.e("    "))
            assertEquals(0, LOG.e("\n"))
        } finally {
            Log.uninstall(SysLog)
        }
    }
}
