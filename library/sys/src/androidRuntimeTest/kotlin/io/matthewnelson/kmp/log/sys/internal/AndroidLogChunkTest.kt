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
package io.matthewnelson.kmp.log.sys.internal

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.sys.SysLog
import io.matthewnelson.kmp.log.sys.isNative
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidLogChunkTest {

    private companion object {
        private const val CHARS: String = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_+-/"
    }

    @Test
    fun givenAndroid_whenLogExceeds4000Characters_thenIsChunked() {
        val log = StringBuilder()
        var line = 0
        while (log.length < 6_000) {
            log.append("LINE[").append(line++).append("] ")
            repeat(87) {
                val i = Random.nextInt(from = 0, until = CHARS.length)
                log.append(CHARS[i])
            }
            log.append(". CHARS[").append(log.length + 2).appendLine(']')
        }
        Log.installOrThrow(SysLog)
        try {
            val tag = "Chunking" + if (isNative()) "Native" else "NonNative"
            val result = Log.Logger.of(tag = tag).i(log.toString())
            assertEquals(1, result)
        } finally {
            Log.uninstallOrThrow(SysLog)
        }
    }
}
