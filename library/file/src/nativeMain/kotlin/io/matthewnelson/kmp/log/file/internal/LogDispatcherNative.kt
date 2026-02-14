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

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext

internal actual value class LogDispatcher private constructor(internal val value: Pair<Int, CoroutineDispatcher>) {

    @OptIn(DelicateCoroutinesApi::class)
    @Throws(IllegalArgumentException::class)
    internal actual constructor(nThreads: Int, name: String): this(
        value = nThreads to newFixedThreadPoolContext(nThreads, name)
    )

    @Throws(Throwable::class)
    internal actual fun close() { (value.second as CloseableCoroutineDispatcher).close() }

    actual override fun toString(): String = "LogDispatcher[nThreads=${value.first}]@${value.second.hashCode()}"
}
