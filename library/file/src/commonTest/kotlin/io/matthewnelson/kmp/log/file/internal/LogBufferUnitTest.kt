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

import io.matthewnelson.kmp.file.FileStream
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(DelicateCoroutinesApi::class)
class LogBufferUnitTest {

    @Test
    fun givenUse_whenFinallyBlockExecutes_thenClosesChannelAndConsumesAllBufferedActions() = runTest {
        val logBuffer = LogBuffer(Channel.UNLIMITED, BufferOverflow.SUSPEND)
        val expected = 11

        var count = 0
        repeat(expected) {
            logBuffer.channel.trySend(object : LogAction.Write {
                override suspend fun invoke(
                    stream: FileStream.ReadWrite,
                    buf: ByteArray,
                    sizeLog: Long,
                    processedWrites: Int,
                ): Long = error("Should not be called")

                override fun drop(undelivered: Boolean) {
                    count++
                    // LogBuffer.use should always pass `false`
                    assertFalse(undelivered)
                }
            })
        }

        try {
            logBuffer.use(logW = { _, _ -> 0 }) {
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
