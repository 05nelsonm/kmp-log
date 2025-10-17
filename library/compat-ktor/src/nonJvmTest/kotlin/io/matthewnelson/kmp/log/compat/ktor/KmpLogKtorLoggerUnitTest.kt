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
package io.matthewnelson.kmp.log.compat.ktor

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.compat.ktor.KmpLogKtorLogger.Compat.asKtorLogger
import io.matthewnelson.kmp.log.compat.ktor.internal.nonJvmFindMinLevelOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KmpLogKtorLoggerUnitTest {

    @Test
    fun givenLogger_whenEquals_thenIsAsExpected() {
        val logger = Log.Logger.of(domain = "compat-ktor", tag = "Equals").asKtorLogger()
        val d: Any = logger.delegate
        val l: Any = logger
        assertNotEquals(d, l)
    }

    @Test
    fun givenLogger_whenHashCode_thenIsAsExpected() {
        val logger = Log.Logger.of(domain = "compat-ktor", tag = "HashCode").asKtorLogger()
        assertNotEquals(logger.hashCode(), logger.delegate.hashCode())
    }

    @Test
    fun givenLogger_whenToString_thenIsAsExpected() {
        val logger = Log.Logger.of(domain = "compat-ktor", tag = "ToString").asKtorLogger()
        assertNotEquals(logger.delegate.toString(), logger.toString())
        assertTrue(logger.toString().startsWith("KmpLogKtorLogger["))
        assertFalse(logger.toString().endsWith(logger.delegate.hashCode().toString()))
        assertTrue(logger.toString().endsWith(logger.hashCode().toString()))
    }

    @Test
    fun givenLogger_whenFindMinLevelOrNull_thenReturnsExpected() {
        val logger = Log.Logger.of(domain = "compat-ktor", tag = "FindLevelOrNull").asKtorLogger()
        assertEquals(Log.Level.Fatal, logger.nonJvmFindMinLevelOrNull())

        Log.install(object : Log(uid = "testing", min = Level.Info) {
            override fun log(
                level: Level,
                domain: String?,
                tag: String,
                msg: String?,
                t: Throwable?
            ): Boolean = false
        })

        try {
            assertEquals(Log.Level.Info, logger.nonJvmFindMinLevelOrNull())
        } finally {
            Log.uninstallAll(evenAbortHandler = false)
        }
    }
}
