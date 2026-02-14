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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "LocalVariableName")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.log.Log.Logger
import io.matthewnelson.kmp.log.file.FileLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration.Companion.milliseconds

internal expect value class LogDispatcher private constructor(internal val value: Pair<Int, CoroutineDispatcher>) {

    @Throws(IllegalArgumentException::class)
    internal constructor(nThreads: Int, name: String)

    @Throws(Throwable::class)
    internal fun close()

    override fun toString(): String
}

internal abstract class LogDispatcherAllocator protected constructor(
    LOG: Logger?,
): SharedResourceAllocator<LogDispatcher>(
    LOG,
    deallocationDelay = 250.milliseconds,
    deallocationDispatcher = Dispatchers.IO,
) {
    abstract override fun doAllocation(): LogDispatcher
    final override fun LogDispatcher.doDeallocation() { close() }
}

internal class RealThreadPool private constructor(nThreads: Int, init: Any): FileLog.ThreadPool(nThreads, init) {

    // Using Lazy such that if the FileLog is never installed at Log.Root,
    // then N is never incremented nor Logger instantiated.
    @get:JvmSynthetic
    internal val allocator: Lazy<LogDispatcherAllocator> = lazy {
        val logger = Logger.of(tag = "ThreadPool{${N._incrementAndGet()}}", domain = FileLog.DOMAIN)
        val name = "FileLog.${logger.tag}"
        object : LogDispatcherAllocator(logger) {
            override fun doAllocation(): LogDispatcher = LogDispatcher(nThreads, name)
        }
    }

    internal companion object {

        @JvmSynthetic
        @Throws(IllegalArgumentException::class, IllegalStateException::class)
        internal fun of(nThreads: Int, init: Any) = RealThreadPool(nThreads, init)

        private val N = FileLog._atomic(initial = 0L)
    }
}
