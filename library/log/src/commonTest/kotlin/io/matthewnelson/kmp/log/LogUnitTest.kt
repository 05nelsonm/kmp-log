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
            Log.uninstallAll()
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
            Log.uninstallAll()
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
            Log.uninstallAll()
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
        try {
            Log.install(log)
            Log.uninstallOrThrow(log)
            assertEquals(false, isAvailable)
        } finally {
            Log.uninstallAll()
        }
    }
}
