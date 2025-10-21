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

import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.Log.Level
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileLogUnitTest {

    @Test
    fun givenWhitelistDomains_whenIsLoggable_thenReturnsExpected() {
        val log = FileLog.Builder(SysTempDir.path)
            .whitelistDomain("kmp.log")
            .build()

        try {
            Log.install(log)
            assertFalse(Log.Logger.of(tag = "Tag", domain = null).isLoggable(Level.Error))
            assertTrue(Log.Logger.of(tag = "Tag", domain = "kmp.log").isLoggable(Level.Error))
        } finally {
            Log.uninstall(log)
        }
    }

    @Test
    fun givenWhitelistTags_whenIsLoggable_thenReturnsExpected() {
        val log = FileLog.Builder(SysTempDir.path)
            .whitelistTag("Tag")
            .build()

        try {
            Log.install(log)
            assertTrue(Log.Logger.of(tag = "Tag", domain = null).isLoggable(Level.Error))
            assertTrue(Log.Logger.of(tag = "Tag", domain = "kmp.log").isLoggable(Level.Error))
            assertFalse(Log.Logger.of(tag = "NotTag").isLoggable(Level.Error))
        } finally {
            Log.uninstall(log)
        }
    }

    @Test
    fun givenWhitelistDomainsAndTags_whenIsLoggable_thenReturnsExpected() {
        val log = FileLog.Builder(SysTempDir.path)
            .whitelistDomain("kmp.log")
            .whitelistTag("Tag")
            .build()

        try {
            Log.install(log)
            assertFalse(Log.Logger.of(tag = "Tag", domain = null).isLoggable(Level.Error))
            assertTrue(Log.Logger.of(tag = "Tag", domain = "kmp.log").isLoggable(Level.Error))
            assertFalse(Log.Logger.of(tag = "NotTag", domain = "kmp.log").isLoggable(Level.Error))
        } finally {
            Log.uninstall(log)
        }
    }
}
