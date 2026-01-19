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

    @Test
    fun givenCapacity_whenEqualsChannelUNLIMITED_thenThrowsIllegalArgumentException() = runTest {
        assertFailsWith<IllegalArgumentException> {
            LogBuffer(capacity = Channel.UNLIMITED, null, scope = this)
        }
    }

    @Test
    fun givenCapacity_whenLessThan1_thenThrowsIllegalArgumentException() = runTest {
        assertFailsWith<IllegalArgumentException> {
            LogBuffer(capacity = 0, null, scope = this)
        }
    }

    @Test
    fun givenMaxBuffered_whenCapacityExceeded_thenOldestDroppedIsConsumed() = runTest {
        val logBuffer = LogBuffer(capacity = 5, null, scope = this)
        var invocations = 0
        var dropped = 0
        var used = 0

        repeat(6) { i ->
            logBuffer.channel.trySend { stream, buf, sizeLog, processed ->
                invocations++
                // LogBuffer.Channel(onUndeliveredElement) implementation uses ByteArray(0),
                // wheres LogBuffer.use utilizes ByteArray(DEFAULT_BUFFER_SIZE) when consuming
                // them.
                if (buf.isEmpty()) {
                    dropped++
                    // The oldest was dropped...
                    assertEquals(0, i)
                } else {
                    used++
                    assertNotEquals(0, i)
                }
                assertEquals(null, stream)
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

        // Need a delay so that onUndeliveredElement can launch
        // its coroutine to consume the dropped LogAction.
        withContext(Dispatchers.Default) { delay(25.milliseconds) }

        assertEquals(6, invocations)
        assertEquals(1, dropped)
        assertEquals(5, used)
    }
}
