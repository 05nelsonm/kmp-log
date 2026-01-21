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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmInline

@JvmInline
internal value class LogSend private constructor(private val job: Deferred<Boolean>) {

    @OptIn(DelicateCoroutinesApi::class)
    internal constructor(
        logScope: LogScope,
        dispatcher: CoroutineDispatcher,
        logBuffer: LogBuffer,
        logAction: LogAction,
    ): this(job = logScope.async(dispatcher, start = CoroutineStart.ATOMIC) {
        try {
            withContext(NonCancellable) { logBuffer.channel.send(logAction) }
            true
        } catch (_: ClosedSendChannelException) {
            // LogBuffer.channel's onUndeliveredElement will consume
            // the LogAction and clean everything up for us.
            false
        }
    })

    internal suspend inline fun await(): Boolean = job.await()

    override fun toString(): String = "LogSend[job=$job]"
}
