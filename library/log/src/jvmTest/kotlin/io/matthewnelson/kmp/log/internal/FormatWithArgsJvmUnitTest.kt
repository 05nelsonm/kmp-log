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
package io.matthewnelson.kmp.log.internal

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.TestLog
import io.matthewnelson.kmp.log.d
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatWithArgsJvmUnitTest {

    @Test
    fun givenArguments_whenFormatWithArgs_thenSucceeds() {
        val expected = "some numbers [4 3 2]"
        val actual = "some numbers [%3$1d %2$1d %1$1d]".withFormatter(_format = { s -> format(s, 2, 3, 4) })
        assertEquals(expected, actual)
    }

    @Test
    fun givenArguments_whenLogged_thenFormattingIsLazilyApplied() {
        val logger = Log.Logger.of("JvmFormatArgs")
        val expected = "1 0"
        val format = "%2$1d %1$1d"
        assertEquals(0, logger.d(t = null, format, 0, 1))

        val log = TestLog(uid = "TestLogFormatArgs")
        Log.installOrThrow(log)
        try {
            assertEquals(1, logger.d(t = null, format, 0, 1))
            assertEquals(1, log.logs?.size)
            assertEquals(expected, log.logs?.first()?.msg)
        } finally {
            Log.uninstallOrThrow(log)
        }
    }
}
