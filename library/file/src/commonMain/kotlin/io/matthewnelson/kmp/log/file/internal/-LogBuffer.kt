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
import kotlin.jvm.JvmInline

/*
* An action to be enqueued into LogBuffer for processing.
* */
internal typealias LogAction = suspend (
    // The open log. May be null if there was an error and this
    // action is being dropped (see consumeAndIgnore).
    stream: FileStream.ReadWrite?,

    // A pre-allocated buffer to use for UTF-8 encoding.
    buf: ByteArray,

    // The number of LogActions prior to this one which resulted
    // in data written to the log (i.e. had a return of > 0L).
    //
    // LogActions are processed in chunks whereby the log loop
    // will yield to another process and then continue processing
    // buffered LogAction, so.
    processed: Int,
) -> Long

internal suspend inline fun LogAction.consumeAndIgnore(buf: ByteArray, processed: Int = 0) {
    try {
        invoke(null, buf, processed)
    } catch (_: Throwable) {}
}

@JvmInline
internal value class LogBuffer private constructor(internal val channel: Channel<LogAction>) {
    internal constructor(): this(Channel(UNLIMITED))
}

@OptIn(ExperimentalContracts::class)
internal suspend inline fun LogBuffer.use(LOG: Log.Logger?, block: LogBuffer.(buf: ByteArray) -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
    var threw: Throwable? = null

    try {
        block(buf)
    } catch (t: Throwable) {
        threw = t
    } finally {
        channel.close()
        var count = 0L
        while (true) {
            val logAction = channel.tryReceive().getOrNull() ?: break
            count++
            logAction.consumeAndIgnore(buf)
        }
        if (LOG != null && count > 0L) {
            LOG.w { "Skipped $count logs" }
        }
        buf.fill(0)
    }

    threw?.let { throw it }
    return
}
