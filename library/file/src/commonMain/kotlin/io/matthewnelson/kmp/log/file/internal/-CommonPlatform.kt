/*
 * Copyright (c) 2025 Matthew Nelson
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

import io.matthewnelson.kmp.log.file.FileLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

internal expect inline fun FileLog.Companion.isDesktop(): Boolean

internal expect fun FileLog.Companion.now(omitYear: Boolean): CharSequence

internal expect fun FileLog.Companion.pid(): Int

// NOTE: logJob MUST be in a started state.
@OptIn(ExperimentalContracts::class)
@Throws(CancellationException::class)
internal suspend inline fun FileLog.Companion.awaitAndCancel(
    logJob: Job,
    timeout: Duration,
    canceledBy: () -> String,
) {
    contract { callsInPlace(canceledBy, InvocationKind.AT_MOST_ONCE) }

    if (!timeout.isPositive()) {
        if (logJob.isActive) {
            val cb = canceledBy()
            logJob.cancel("Canceled by $cb")
        }
        return logJob.join()
    }

    var threw: Throwable? = null
    try {
        withTimeout(timeout) { logJob.join() }
    } catch (t: Throwable) {
        threw = t
    }
    if (logJob.isActive) {
        val cb = canceledBy()
        logJob.cancel("Canceled by $cb", threw)
    }
    return logJob.join()
}
