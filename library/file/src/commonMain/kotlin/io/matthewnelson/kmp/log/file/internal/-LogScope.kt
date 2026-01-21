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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.jvm.JvmInline

@JvmInline
internal value class LogScope private constructor(
    @Deprecated("Use LogScope.{launch/async}", level = DeprecationLevel.ERROR)
    internal val scope: CoroutineScope
) {
    internal constructor(
        uidSuffix: String,
        handler: CoroutineExceptionHandler,
    ): this(scope = CoroutineScope(context =
        CoroutineName(uidSuffix)
        + SupervisorJob()
        + handler
        // A CoroutineDispatcher is intentionally NOT specified
        // because FileLog allocates one in onInstall.
    ))
}

// Intentionally does not define defaults to force declarations
internal inline fun LogScope.launch(
    dispatcher: CoroutineDispatcher,
    start: CoroutineStart,
    noinline block: suspend CoroutineScope.() -> Unit,
): Job {
    @Suppress("DEPRECATION_ERROR")
    return scope.launch(context = dispatcher, start, block)
}

// Intentionally does not define defaults to force declarations
internal inline fun <T> LogScope.async(
    dispatcher: CoroutineDispatcher,
    start: CoroutineStart,
    noinline block: suspend CoroutineScope.() -> T,
): Deferred<T> {
    @Suppress("DEPRECATION_ERROR")
    return scope.async(context = dispatcher, start, block)
}
