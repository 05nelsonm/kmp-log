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
package io.matthewnelson.kmp.log.compat.ktor.internal

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.compat.ktor.KmpLogKtorLogger
import io.matthewnelson.kmp.log.compat.ktor.asKtorLogger
import kotlin.test.Test
import kotlin.test.assertEquals

class KmpLogKtorLoggerJsWasmJsUnitTest {

    @Test
    fun givenLogger_whenCreated_thenInstanceIsCached() {
        val log = Log.Logger.of("jsWasmJs-cached")
        log.asKtorLogger()
        val expected = KmpLogKtorLogger.size()
        log.asKtorLogger() // Should return cached instance
        assertEquals(expected, KmpLogKtorLogger.size())
    }
}
