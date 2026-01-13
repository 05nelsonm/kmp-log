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
@file:Suppress("NOTHING_TO_INLINE", "WRONG_INVOCATION_KIND", "LocalVariableName")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.encoding.core.EncoderDecoder.Companion.DEFAULT_BUFFER_SIZE
import io.matthewnelson.kmp.file.FileStream
import io.matthewnelson.kmp.log.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal typealias LogWriteAction = suspend (stream: FileStream.Write?, buf: ByteArray) -> Long
internal typealias LogBuffer = Channel<LogWriteAction>

internal inline fun LogBuffer(): LogBuffer = Channel(UNLIMITED)

@OptIn(ExperimentalContracts::class)
internal suspend inline fun LogBuffer.use(LOG: Log.Logger?, block: (buf: ByteArray) -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
    var threw: Throwable? = null

    try {
        block(buf)
    } catch (t: Throwable) {
        threw = t
    } finally {
        close()
        var count = 0L
        while (true) {
            val writeAction = tryReceive().getOrNull() ?: break
            count++
            try {
                writeAction.invoke(null, buf)
            } catch (_: Throwable) {}
        }
        if (LOG != null && count > 0L) {
            LOG.w { "Skipped $count logs" }
        }
    }

    threw?.let { throw it }
    return
}
