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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.log.file.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

@JvmInline
internal actual value class LogDispatcher private constructor(internal val value: Pair<Int, CoroutineDispatcher>) {

    @Throws(IllegalArgumentException::class)
    internal actual constructor(nThreads: Int, name: String): this(value = Unit.run {
        require(nThreads >= 1) { "nThreads[$nThreads] < 1" }
        val threadNo = if (nThreads > 1) AtomicLong(0L) else null
        val executor = Executors.newScheduledThreadPool(nThreads) { task ->
            val t = Thread(task)
            t.isDaemon = true
            t.name = threadNo?.let { name + '-' + it._incrementAndGet() } ?: name
            t.priority = Thread.MAX_PRIORITY
            t
        }
        nThreads to executor.asCoroutineDispatcher()
    })

    @Throws(Throwable::class) // SecurityException
    internal actual fun close() { (value.second as ExecutorCoroutineDispatcher).close() }

    actual override fun toString(): String = "LogDispatcher[nThreads=${value.first}]@${value.second.hashCode()}"
}
