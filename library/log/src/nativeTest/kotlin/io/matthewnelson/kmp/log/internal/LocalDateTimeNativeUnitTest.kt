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
@file:Suppress("RemoveRedundantCallsOfConversionMethods")

package io.matthewnelson.kmp.log.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class LocalDateTimeNativeUnitTest {

    @Test
    fun givenKmpLogLocalDateTime_whenCalled_thenReturnsSuccessfully() {
        val dt = IntArray(KMP_LOG_LOCAL_DATE_TIME_SIZE)
        val ret = dt.usePinned { pinned ->
            kmp_log_local_date_time(pinned.addressOf(0))
        }.toInt()

        assertEquals(0, ret)

        // year, month, day should all be gt 0
        for (i in 0..2) {
            assertTrue(dt[i] > 0, "index[$i]")
        }
        assertTrue(dt[3] in 0..23, "index[3]")
        assertTrue(dt[4] in 0..59, "index[4]")
        assertTrue(dt[5] in 0..59, "index[5]")
        assertTrue(dt[6] in 0..999, "index[6]")
    }
}
