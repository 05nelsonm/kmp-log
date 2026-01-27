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
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidIsLoggableTest {

    private companion object {
        // See androidLibrary.android block in build.gradle.kts
        private val LOG = Log.Logger.of("SYS_LOG_TEST")
    }

    @Test
    fun givenAndroid_whenIsLoggable_thenRespectsSystemPropertySettings() {
        if (deviceApiLevel() < IS_LOGGABLE_REQUIRED_API_LEVEL) {
            println("Skipping...")
            return
        }

        Log.uninstallOrThrow(Log.AbortHandler.UID)
        Log.installOrThrow(SysLog.of(Log.Level.Verbose))
        try {
            Log.Level.entries.forEach { level ->
                val expected = when (level) {
                    Log.Level.Verbose,
                    Log.Level.Debug,
                    Log.Level.Info -> false
                    Log.Level.Warn,
                    Log.Level.Error,
                    Log.Level.Fatal -> true
                }

                assertEquals(
                    expected,
                    SysLog.androidIsLoggable(level, LOG.domain, LOG.tag, default = !expected),
                    "$LOG >> $level",
                )
                assertEquals(expected, LOG.isLoggable(level), "$LOG >> $level")
            }
        } finally {
            Log.install(Log.AbortHandler)
            Log.uninstallOrThrow(SysLog.UID)
        }
    }
}
