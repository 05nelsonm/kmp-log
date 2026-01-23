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

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.jvm.JvmInline

@JvmInline
internal value class ScopeLog private constructor(
    @Deprecated(
        message = "Use ScopeLog.{async/launch/coroutineName/handler/supervisorJob}",
        level = DeprecationLevel.ERROR,
    )
    internal val scope: CoroutineScope
) {

    internal constructor(
        uidSuffix: String,
        handler: CoroutineExceptionHandler,
    ): this(scope = CoroutineScope(context =
        CoroutineName(name = uidSuffix)
        + SupervisorJob(parent = null)
        + handler
        // A CoroutineDispatcher is intentionally NOT specified
        // because FileLog allocates one via onInstall.
        //
        // See ScopeLogHandle
    ))

    internal inline val coroutineName: CoroutineName get() {
        @Suppress("DEPRECATION_ERROR")
        return scope.coroutineContext[CoroutineName]!!
    }

    internal inline val handler: CoroutineExceptionHandler get() {
        @Suppress("DEPRECATION_ERROR")
        return scope.coroutineContext[CoroutineExceptionHandler]!!
    }

    internal inline val supervisorJob: CompletableJob get() {
        @Suppress("DEPRECATION_ERROR")
        return scope.coroutineContext.job as CompletableJob
    }
}

//// Intentionally does not define defaults to force declarations
//internal inline fun <T> ScopeLog.async(
//    dispatcher: CoroutineDispatcher,
//    start: CoroutineStart,
//    noinline block: suspend CoroutineScope.() -> T,
//): Deferred<T> {
//    @Suppress("DEPRECATION_ERROR")
//    return scope.async(context = dispatcher, start, block)
//}

// Intentionally does not define defaults to force declarations
internal inline fun ScopeLog.launch(
    dispatcher: CoroutineDispatcher,
    start: CoroutineStart,
    noinline block: suspend CoroutineScope.() -> Unit,
): Job {
    @Suppress("DEPRECATION_ERROR")
    return scope.launch(context = dispatcher, start, block)
}
