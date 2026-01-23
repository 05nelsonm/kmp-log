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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmInline

@JvmInline
internal value class ScopeLogHandle private constructor(
    @Deprecated(
        message = "Use ScopeLogHandle.{async/launch/dispatcher/supervisorJob}",
        level = DeprecationLevel.ERROR,
    )
    internal val scope: CoroutineScope,
) {

    internal constructor(
        logJob: Job,
        scopeLog: ScopeLog,
        onInstallInvocations: Long,
        dispatcher: CoroutineDispatcher,
        dispatcherCloseLazily: CompletionHandler,
    ): this(scope = CoroutineScope(context =
        CoroutineName(scopeLog.coroutineName.name + "-Handle{$onInstallInvocations}")
        // Using ScopeLog's SupervisorJob as parent to ensure
        // that the only relation to logJob is via its completion
        // handler where this SupervisorJob will be canceled.
        + SupervisorJob(parent = scopeLog.supervisorJob)
        + scopeLog.handler
        + dispatcher
    )) {
        logJob.invokeOnCompletion { t ->
            val cause = (t as? CancellationException)
                ?: CancellationException("LogJob completed", t)
            supervisorJob.cancel(cause)
        }
        supervisorJob.invokeOnCompletion(dispatcherCloseLazily)
    }

    internal inline val dispatcher: CoroutineDispatcher get() {
        @Suppress("DEPRECATION_ERROR")
        return scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
    }

    internal inline val supervisorJob: CompletableJob get() {
        @Suppress("DEPRECATION_ERROR")
        return scope.coroutineContext.job as CompletableJob
    }
}

// Intentionally does not define defaults to force declarations
internal inline fun <T> ScopeLogHandle.async(
    start: CoroutineStart,
    noinline block: suspend CoroutineScope.() -> T,
): Deferred<T> {
    @Suppress("DEPRECATION_ERROR")
    return scope.async(EmptyCoroutineContext, start, block)
}

//// Intentionally does not define defaults to force declarations
//internal inline fun ScopeLogHandle.launch(
//    start: CoroutineStart,
//    noinline block: suspend CoroutineScope.() -> Unit,
//): Job {
//    @Suppress("DEPRECATION_ERROR")
//    return scope.launch(EmptyCoroutineContext, start, block)
//}
