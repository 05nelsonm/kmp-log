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
import kotlin.test.assertTrue

class AndroidAbortHandlerTest {

    private companion object {
        private val LOG = Log.Logger.of("AndroidAbortHandlerTest")
    }

    @Test
    fun givenLogLevelFatal_whenOnlyLogAbortHandlerIsInstalled_thenUsesAndroidLogWtf() {
        assertTrue(Log.AbortHandler.isInstalled)
        assertEquals(1, Log.installed().size)
        // If it was not using android.util.Log.wtf, it would have
        // called Runtime.halt and blown out the test.
        assertEquals(1, LOG.wtf { "TEST!" })
    }

    @Test
    fun givenLogLevelFatal_whenLogAbortHandlerIsNotTheOnlyInstalledInstance_thenUsesAndroidLogWtf() {
        assertTrue(Log.AbortHandler.isInstalled)
        val log = object : Log(uid = "test_wtf", min = Level.Fatal) {
            override fun log(
                level: Level,
                domain: String?,
                tag: String,
                msg: String?,
                t: Throwable?
            ): Boolean = true
        }
        Log.install(log)
        try {
            assertEquals(2, Log.installed().size)
            assertEquals(2, LOG.wtf { "TEST!" })
        } finally {
            Log.uninstall(log)
        }
    }
}
