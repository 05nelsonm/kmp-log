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
@file:Suppress("WRONG_INVOCATION_KIND")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.encoding.core.EncoderDecoder.Companion.DEFAULT_BUFFER_SIZE
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmInline

@JvmInline
internal value class LogBuffer private constructor(internal val channel: Channel<LogAction.Write>) {

    @Throws(IllegalArgumentException::class)
    internal constructor(capacity: Int, overflow: BufferOverflow): this(Channel(
        capacity = capacity,
        onBufferOverflow = overflow,
        onUndeliveredElement = { logAction -> logAction.drop(undelivered = true) },
    )) {
        // Channel.RENDEZVOUS == 0
        require(capacity >= Channel.RENDEZVOUS) { "capacity[$capacity] < Channel.RENDEZVOUS" }
    }
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> LogBuffer.use(
    logW: (t: Throwable?, lazyMsg: () -> Any?) -> Int,
    block: (buf: ByteArray) -> T,
): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        callsInPlace(logW, InvocationKind.AT_MOST_ONCE)
    }

    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
    var threw: Throwable? = null

    val result: T? = try {
        block(buf)
    } catch (t: Throwable) {
        threw = t
        null
    } finally {
        channel.close()
        var count = 0L
        while (true) {
            val logAction = channel.tryReceive().getOrNull() ?: break
            count++
            logAction.drop(undelivered = false)
        }
        buf.fill(0)
        if (count > 0L) logW(threw) {
            "$count log(s) awaiting processing were dropped due to Channel closure."
        }
    }

    threw?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return result as T
}
