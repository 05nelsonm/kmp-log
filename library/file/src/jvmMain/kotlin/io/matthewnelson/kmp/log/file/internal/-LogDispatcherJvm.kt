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

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.Throws

@Throws(IllegalArgumentException::class)
internal actual inline fun LogDispatcherAllocator.Companion.newLogDispatcher(nThreads: Int, name: String): LogDispatcher {
    require(nThreads >= 1) { "nThreads[$nThreads] < 1" }
    val threadNo = if (nThreads > 1) AtomicLong(0L) else null
    val executor = Executors.newScheduledThreadPool(nThreads) { task ->
        val t = Thread(task)
        t.isDaemon = true
        t.name = threadNo?.let { name + '-' + it._incrementAndGet() } ?: name
        t.priority = Thread.MAX_PRIORITY
        t
    }
    // Before making the Executor unconfigurable, want to ensure
    // setRemoveOnCancelPolicy(true) is called (if possible).
    executor.asCoroutineDispatcher()
    return Executors.unconfigurableScheduledExecutorService(executor).asCoroutineDispatcher()
}

@Throws(SecurityException::class)
internal actual inline fun LogDispatcher.destroy() {
    (this as? ExecutorCoroutineDispatcher)?.close()
}
