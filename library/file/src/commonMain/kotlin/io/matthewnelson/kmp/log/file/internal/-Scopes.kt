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
@file:Suppress("NOTHING_TO_INLINE", "UnusedReceiverParameter")

package io.matthewnelson.kmp.log.file.internal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmInline

/*
* Wrappers for several different CoroutineScopes used by FileLog. This is
* to maintain domain level separation of scopes, and enforce relationships.
* */

@JvmInline
internal value class ScopeFileLog private constructor(
    @Deprecated(
        message = "Use ScopeLog.{launch/coroutineName/handler/supervisorJob}",
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

@JvmInline
internal value class ScopeLog private constructor(internal val scopeLog: CoroutineScope) {

    internal companion object {
        @OptIn(ExperimentalContracts::class)
        internal suspend inline fun <R> scopeLog(
            crossinline block: suspend ScopeLog.() -> R,
        ): R {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            return supervisorScope { block(ScopeLog(this)) }
        }
    }

    internal inline val jobLog: Job get() = scopeLog.coroutineContext.job
}

@JvmInline
internal value class ScopeLogLoop private constructor(internal val scopeLogLoop: CoroutineScope) {

    internal companion object {
        @OptIn(ExperimentalContracts::class)
        internal suspend inline fun <R> ScopeLog.scopeLogLoop(
            crossinline block: suspend ScopeLogLoop.() -> R,
        ): R {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            return coroutineScope { block(ScopeLogLoop(this)) }
        }
    }

    internal inline val jobLogLoop: Job get() = scopeLogLoop.coroutineContext.job
}

@JvmInline
internal value class ScopeLogHandle private constructor(
    @Deprecated(
        message = "Use ScopeLogHandle.{async/dispatcher/handler/supervisorJob}",
        level = DeprecationLevel.ERROR,
    )
    internal val scopeLogHandle: CoroutineScope,
) {

    internal constructor(
        logJob: Job,
        scope: ScopeFileLog,
        onInstallInvocations: Long,
        dispatcher: CoroutineDispatcher,
        dispatcherDeRef: SharedResourceAllocator.DeRefHandle,
    ): this(scopeLogHandle = CoroutineScope(context =
        CoroutineName(scope.coroutineName.name + "-Handle{$onInstallInvocations}")

        // Using ScopeFileLog's SupervisorJob as parent to ensure
        // that the only relation to logJob is via its completion
        // handler where THIS SupervisorJob will then be canceled.
        //
        // If we were to utilize logJob as the parent, then it
        // would not complete until THIS job is canceled. Instead,
        // what we want is for logJob completion to be based on
        // LogBuffer.channel's closure and subsequent exhaustion
        // of all its remaining LogAction, and THEN cancellation
        // of this job and all its children.
        + SupervisorJob(parent = scope.supervisorJob)

        + scope.handler
        + dispatcher
    )) {
        logJob.invokeOnCompletion { t ->
            val cause = (t as? CancellationException)
                ?: CancellationException("LogJob completed", t)
            supervisorJob.cancel(cause)
        }
        supervisorJob.invokeOnCompletion { dispatcherDeRef.invoke() }
    }

    internal inline val dispatcher: CoroutineDispatcher get() {
        @Suppress("DEPRECATION_ERROR")
        return scopeLogHandle.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
    }

    internal inline val supervisorJob: CompletableJob get() {
        @Suppress("DEPRECATION_ERROR")
        return scopeLogHandle.coroutineContext.job as CompletableJob
    }
}

// Intentionally does not define defaults to force declarations
internal inline fun ScopeFileLog.launch(
    dispatcher: CoroutineDispatcher,
    start: CoroutineStart,
    noinline block: suspend CoroutineScope.() -> Unit,
): Job {
    @Suppress("DEPRECATION_ERROR")
    return scope.launch(context = dispatcher, start, block)
}

// Intentionally does not define defaults to force declarations
internal inline fun <T> ScopeLogHandle.async(
    start: CoroutineStart,
    noinline block: suspend CoroutineScope.() -> T,
): Deferred<T> {
    @Suppress("DEPRECATION_ERROR")
    return scopeLogHandle.async(EmptyCoroutineContext, start, block)
}
