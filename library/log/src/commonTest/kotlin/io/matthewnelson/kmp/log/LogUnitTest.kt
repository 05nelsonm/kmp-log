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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogUnitTest {

    @Test
    fun givenLog_whenUidIsEmpty_thenThrowsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> { TestLog(uid = "") }
    }

    @Test
    fun givenLog_whenUidContainsWhitespace_thenThrowsIllegalArgumentException() {
        arrayOf(' ', '\n', '\t', '\r').forEach { whitespace ->
            assertFailsWith<IllegalArgumentException> { TestLog(uid = "some${whitespace}uid") }
        }
    }

    @Test
    fun givenLog_whenInstallSameUID_thenSecondIsNotInstalled() {
        val one = TestLog(uid = "one")
        val two = TestLog(uid = one.uid)
        assertNull(one.logs, "one.logs")
        assertNull(two.logs, "two.logs")

        try {
            Log.install(one)
            assertNotNull(one.logs, "one.logs")
            assertFalse(Log.install(two), "install(two)")
            assertNull(two.logs, "two.logs")
        } finally {
            Log.uninstall(one)
            Log.uninstall(two)
        }
    }

    @Test
    fun givenLog_whenUninstalled_thenOnUninstallCalledForTheInstalledInstance() {
        val one = TestLog(uid = "one")
        val two = TestLog(uid = one.uid)
        assertNull(one.logs, "one.logs")
        assertNull(two.logs, "two.logs")

        try {
            Log.install(one)
            assertNotNull(one.logs, "one.logs")
            assertFalse(Log.install(two), "install(two)")
            assertNull(two.logs, "two.logs")
            assertTrue(Log.uninstall(two), "uninstall(two)")
            assertNull(one.logs, "one.logs")
            assertNull(two.logs, "two.logs")
        } finally {
            Log.uninstall(one)
            Log.uninstall(two)
        }
    }

    @Test
    fun givenLog_whenOnInstallInvoked_thenIsDoneBeforeBeingAddedToAvailableLogs() {
        var isAvailable: Boolean? = null
        val log = object : TestLog(uid = "OnInstall") {
            override fun onInstall() {
                super.onInstall()
                isAvailable = installed().contains(this)
            }
        }
        try {
            Log.install(log)
            assertEquals(false, isAvailable)
        } finally {
            Log.uninstallOrThrow(log)
        }
    }

    @Test
    fun givenLog_whenOnUninstallInvoked_thenIsDoneAfterBeingRemovedFromAvailableLogs() {
        var isAvailable: Boolean? = null
        val log = object : TestLog(uid = "OnUninstall") {
            override fun onUninstall() {
                super.onUninstall()
                isAvailable = installed().contains(this)
            }
        }
        Log.install(log)
        Log.uninstallOrThrow(log)
        assertEquals(false, isAvailable)
    }

    @Test
    fun givenLogImplementation_whenOnInstallUninstallCallsRootInstallUninstall_thenThrowsIllegalStateException() {
        val log1 = object : TestLog(uid = "OnUninstall") {
            override fun onUninstall() {
                uninstall(AbortHandler)
            }
        }
        val log2 = object : TestLog(uid = "OnInstall") {
            override fun onInstall() {
                install(log1)
            }
        }

        try {
            assertEquals(1, Root.installed().size)
            assertEquals(listOf(Log.AbortHandler), Root.installed())
            assertFailsWith<IllegalStateException> { Root.install(log2) }
            assertEquals(1, Root.installed().size)
            assertEquals(listOf(Log.AbortHandler), Root.installed())
            Root.install(log1)
            assertFailsWith<IllegalStateException> { Root.uninstall(log1) }
            assertEquals(1, Root.installed().size)
            assertEquals(listOf(Log.AbortHandler), Root.installed())
        } finally {
            Root.uninstallAll(evenAbortHandler = false)
        }
    }

    @Test
    fun givenInstalledLevels_whenNoLogInstalled_thenIsEmpty() {
        Root.uninstallAll(evenAbortHandler = true)
        try {
            assertTrue(Root.installedLevels().isEmpty())
        } finally {
            Root.installOrThrow(Log.AbortHandler)
        }
    }

    @Test
    fun givenInstalledLevels_whenLogsInstalled_thenContainsExpectedLevels() {
        assertEquals(listOf(Log.AbortHandler), Root.installed())
        assertEquals(setOf(Log.Level.Fatal), Root.installedLevels())

        try {
            Root.install(
                TestLog(
                    uid = "test.log1",
                    min = Log.Level.Debug,
                    max = Log.Level.Info,
                )
            )
            assertEquals(
                setOf(Log.Level.Debug, Log.Level.Info, Log.Level.Fatal),
                Root.installedLevels(),
            )

            Root.install(
                TestLog(
                    uid = "test.log2",
                    min = Log.Level.Verbose,
                    max = Log.Level.Warn,
                )
            )
            assertEquals(
                setOf(Log.Level.Verbose, Log.Level.Debug, Log.Level.Info, Log.Level.Warn, Log.Level.Fatal),
                Root.installedLevels(),
            )

            Root.uninstallAll(evenAbortHandler = false)
            assertEquals(
                setOf(Log.Level.Fatal),
                Root.installedLevels(),
            )
        } finally {
            Root.uninstallAll(evenAbortHandler = false)
        }
    }
}
