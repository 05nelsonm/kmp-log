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
package io.matthewnelson.kmp.log.compat.slf4j

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.compat.slf4j.KmpLogSLF4JLogger.Compat.asSLF4JLogger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KmpLogSLF4JLoggerUnitTest {

    @Test
    fun givenLogger_whenEquals_thenIsAsExpected() {
        val logger = Log.Logger.of(domain = "compat-slf4j", tag = "Equals").asSLF4JLogger()
        val d: Any = logger.delegate
        val l: Any = logger
        assertNotEquals(d, l)
    }

    @Test
    fun givenLogger_whenHashCode_thenIsAsExpected() {
        val logger = Log.Logger.of(domain = "compat-slf4j", tag = "HashCode").asSLF4JLogger()
        assertNotEquals(logger.hashCode(), logger.delegate.hashCode())
    }

    @Test
    fun givenLogger_whenToString_thenIsAsExpected() {
        val logger = Log.Logger.of(domain = "compat-slf4j", tag = "ToString").asSLF4JLogger()
        assertNotEquals(logger.delegate.toString(), logger.toString())
        assertTrue(logger.toString().startsWith("KmpLogSLF4JLogger["))
        assertFalse(logger.toString().endsWith(logger.delegate.hashCode().toString()))
        assertTrue(logger.toString().endsWith(logger.hashCode().toString()))
    }
}
