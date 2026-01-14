/*
 * Copyright (c) 2026 Matthew Nelson
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
@file:Suppress("RedundantCompanionReference")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.log.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TEST_TIME = "00-00 00:00:00.000"

class FormatUnitTest {

    @Test
    fun givenFormat_whenTrace_thenCalculatedCapacityEqualsResultLength() {
        var invocations = 0

        val actual = Log.Root.format(
            time = TEST_TIME,
            pid = 2,
            tid = 2L,
            level = Log.Level.Debug,
            domain = null,
            tag = "Tag",
            msg = "some\nmessage\n",
            t = Throwable(),
            trace = { capacity, length ->
                // Pre-calculated capacity should equal final length,
                // meaning that the StringBuilders used did EXACTLY 0
                // allocations.
                invocations++
                assertEquals(capacity, length)
            },
        )

        assertEquals(2, invocations)
        assertTrue(actual!!.endsWith('\n'))
    }

    @Test
    fun givenFormat_whenCalculatedCapacityIsO_thenReturnsNull() {
        val actual = Log.Root.format(
            time = TEST_TIME,
            pid = 2,
            tid = 2L,
            level = Log.Level.Debug,
            domain = null,
            tag = "Tag",
            msg = "\n\n", // Will be empty lines
            t = null,
        )

        assertNull(actual)
    }

    @Test
    fun givenFormatPrefix_whenPidLessThan1_thenIsUnknown() {
        val actual = Log.Root.formatPrefix(
            time = TEST_TIME,
            pid = -1,
            tid = 2L,
            level = Log.Level.Debug,
            domain = null,
            tag = "Tag",
        )

        assertTrue(actual.contains("unknown"))
        assertTrue(actual.contains("0000002")) // tid
    }

    @Test
    fun givenFormatPrefix_whenTidLessThan0_thenIsUnknown() {
        val actual = Log.Root.formatPrefix(
            time = TEST_TIME,
            pid = 5555,
            tid = -1L,
            level = Log.Level.Debug,
            domain = null,
            tag = "Tag",
        )

        assertTrue(actual.contains("unknown"))
        assertTrue(actual.contains("0005555")) // pid
    }

    @Test
    fun givenFormatPrefix_whenDomainPresent_thenIsFormattedAsExpected() {
        val actual = Log.Root.formatPrefix(
            time = TEST_TIME,
            pid = 5555,
            tid = 1L,
            level = Log.Level.Debug,
            domain = "some.domain",
            tag = "Tag",
        )

        assertTrue(actual.endsWith("[some.domain]Tag: "))
    }
}
