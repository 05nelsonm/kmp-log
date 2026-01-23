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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.log.file.internal

import kotlinx.coroutines.channels.Channel
import kotlin.jvm.JvmInline

@JvmInline
internal value class RotateActionQueue private constructor(private val channel: Channel<LogAction>) {

    internal constructor(scope: ScopeLogLoop): this(LogBuffer(capacity = Channel.UNLIMITED).channel) {
        scope.logLoopJob.invokeOnCompletion { channel.cancel(cause = null) }
    }

    internal inline fun enqueue(noinline action: LogAction) {
        // Will never fail because Channel.UNLIMITED
        channel.trySend(action)
    }

    internal inline fun dequeueOrNull(): LogAction? = channel.tryReceive().getOrNull()
}
