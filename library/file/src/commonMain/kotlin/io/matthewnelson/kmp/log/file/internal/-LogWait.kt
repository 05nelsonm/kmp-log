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
import kotlinx.coroutines.Job
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmInline

@JvmInline
internal value class LogWait private constructor(private val job: CompletableJob) {

    internal constructor(logScope: LogScope): this(Job(parent = logScope.supervisorJob))

    internal inline val isActive: Boolean get() = job.isActive

    @Throws(IllegalStateException::class)
    internal inline fun isSuccess(): Boolean {
        check(!job.isActive) { "isActive[true] >> $job" }
        return !job.isCancelled
    }

    internal inline fun fail() { job.cancel() }
    internal inline fun succeed() { job.complete() }

    @Throws(CancellationException::class)
    internal suspend inline fun join() { job.join() }

    override fun toString(): String = "LogWait[job=$job]"
}
