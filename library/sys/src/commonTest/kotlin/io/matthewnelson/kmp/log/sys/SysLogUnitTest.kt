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
    fun givenSysLog_whenLogThings_thenReturnsTrue() {
        Log.installOrThrow(SysLog.of(Level.Verbose))
        try {
            assertTrue(LOG.v { "TEST VERBOSE" }, "v")
            assertTrue(LOG.d { "TEST DEBUG" }, "d")
            assertTrue(LOG.i { "TEST INFO" }, "i")
            assertTrue(LOG.w { "TEST WARN" }, "w")
            assertTrue(LOG.e(IllegalStateException("log error")) { "TEST ERROR" }, "e")
            assertTrue(LOG.wtf(UnsupportedOperationException("log fatal")) { "TEST FATAL" }, "wtf")
        } finally {
            Log.uninstallOrThrow(SysLog.UID)
        }
    }
}
