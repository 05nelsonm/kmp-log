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

import io.matthewnelson.kmp.log.internal.doAbort
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbortHandlerUnitTest {

    @Test
    fun givenAbortHandler_whenMinMax_thenIsLevelFatal() {
        assertEquals(Log.Level.Fatal, Log.AbortHandler.min)
        assertEquals(Log.Level.Fatal, Log.AbortHandler.max)
    }

    @Test
    fun givenLogger_whenOnlyAbortHandlerInstalled_thenIsLoggableLevelFatalReturnsTrue() {
        assertTrue(Log.AbortHandler.isInstalled)
        assertEquals(1, Log.installed().size)
        assertTrue(Log.Logger.of(tag = "IsLoggableLevelFatal").isLoggable(Log.Level.Fatal))
    }

    @Test
    fun givenInstall_whenLogInstanceHasAbortHandlerUIDButIsNotAbortHandler_thenThrowsIllegalArgumentException() {
        Log.uninstallOrThrow(Log.AbortHandler.UID)
        try {
            assertFalse(Log.AbortHandler.isInstalled)
            val impersonator = TestLog(Log.AbortHandler.UID, Log.Level.Fatal)
            assertFailsWith<IllegalArgumentException> { Log.install(impersonator) }
        } finally {
            Log.uninstall(Log.AbortHandler)
            Log.installOrThrow(Log.AbortHandler)
        }
    }

    @Test
    fun givenUninstallAll_whenEvenAbortHandlerFalse_thenAbortHandlerIsNotUninstalled() {
        val log = TestLog("evenAbortHandler=false")
        try {
            assertTrue(Log.AbortHandler.isInstalled)
            Log.install(log)
            assertContentEquals(listOf(log, Log.AbortHandler), Log.installed())
            Log.uninstallAll(evenAbortHandler = false)
            assertEquals(listOf(Log.AbortHandler), Log.installed())
            assertTrue(Log.AbortHandler.isInstalled)
        } finally {
            Log.uninstall(log)
        }
    }

    @Test
    fun givenUninstallAll_whenEvenAbortHandlerTrue_thenAbortHandlerIsUninstalled() {
        val log = TestLog("evenAbortHandler=true")
        try {
            assertTrue(Log.AbortHandler.isInstalled)
            Log.install(log)
            assertContentEquals(listOf(log, Log.AbortHandler), Log.installed())
            Log.uninstallAll(evenAbortHandler = true)
            assertEquals(emptyList(), Log.installed())
            assertFalse(Log.AbortHandler.isInstalled)
        } finally {
            Log.uninstall(log)
            Log.install(Log.AbortHandler)
        }
    }

    @Test
    fun givenInstall_whenAbortHandlerIsBeingReInstalled_thenIsAlwaysTheLastLogInstance() {
        assertTrue(Log.AbortHandler.isInstalled)
        try {
            Log.uninstall(Log.AbortHandler)
            assertFalse(Log.AbortHandler.isInstalled)
            assertEquals(emptyList(), Log.installed())
            val one = TestLog("always_last_1")
            val two = TestLog("always_last_2")
            Log.install(one)
            assertEquals(listOf(one), Log.installed())
            Log.install(two)
            assertEquals(listOf(two, one), Log.installed())
            Log.install(Log.AbortHandler)
            assertEquals(listOf(two, one, Log.AbortHandler), Log.installed())
        } finally {
            Log.uninstallAll(evenAbortHandler = false)
            Log.install(Log.AbortHandler)
        }
    }

    @Test
    @Ignore // Will crash. Comment out to run manually if modifying anything to do with doAbort.
    fun givenAbortHandler_whenDoAbort_thenProgramCrashes() {
        Log.AbortHandler.doAbort(null)
    }
}
