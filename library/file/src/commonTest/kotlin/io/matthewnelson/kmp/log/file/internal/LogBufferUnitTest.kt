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
package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.encoding.core.EncoderDecoder.Companion.DEFAULT_BUFFER_SIZE
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(DelicateCoroutinesApi::class)
class LogBufferUnitTest {

    @Test
    fun givenUse_whenFinallyBlockExecutes_thenClosesChannelAndConsumesAllBufferedActions() = runTest {
        val logBuffer = LogBuffer()
        val expected = 11

        var count = 0
        repeat(expected) {
            logBuffer.channel.trySend { stream, buf, sizeLog, processed ->
                count++
                assertEquals(null, stream)
                assertEquals(DEFAULT_BUFFER_SIZE, buf.size)
                assertEquals(0L, sizeLog)
                assertEquals(0, processed)
                0L
            }
        }

        try {
            logBuffer.use(null) {
                throw UnsupportedOperationException()
            }
        } catch (_: UnsupportedOperationException) {
            // pass
        }

        assertTrue(logBuffer.channel.isClosedForSend, "isClosedForSend")
        assertTrue(logBuffer.channel.isClosedForReceive, "isClosedForReceive")
        assertEquals(0, logBuffer.channel.toList().size)
        assertEquals(expected, count)
    }
}
