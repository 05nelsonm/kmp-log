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
@file:Suppress("NOTHING_TO_INLINE", "LocalVariableName")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.log.Log.Logger
import io.matthewnelson.kmp.log.file.FileLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.time.Duration.Companion.milliseconds

// Jvm: ExecutorCoroutineDispatcher
// Native: CloseableCoroutineDispatcher
internal typealias LogDispatcher = CoroutineDispatcher

@Throws(IllegalArgumentException::class)
internal expect inline fun FileLog.Companion.newLogDispatcher(nThreads: Int, name: String): LogDispatcher

internal expect inline fun LogDispatcher.destroy()

internal abstract class LogDispatcherAllocator protected constructor(
    LOG: Logger?,
): SharedResourceAllocator<LogDispatcher>(
    LOG,
    deallocationDelay = 250.milliseconds,
    deallocationDispatcher = Dispatchers.IO,
) {
    abstract override fun doAllocation(): LogDispatcher
    final override fun LogDispatcher.doDeallocation() { destroy() }
}
