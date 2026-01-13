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

import java.lang.reflect.Method
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// Java 19+
private val METHOD_THREAD_ID: Method? by lazy {
    try {
        Thread::class.java.getMethod("threadId")
    } catch (_: Throwable) {
        null
    }
}

internal actual fun CurrentThread.id(): Long {
    val current = Thread.currentThread()
    METHOD_THREAD_ID?.let { m ->
        return m.invoke(current) as Long
    }

    @Suppress("DEPRECATION")
    return current.id
}

@OptIn(ExperimentalContracts::class)
internal actual inline fun <T> CurrentThread.uninterruptedImpl(block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val thread = if (Thread.interrupted()) Thread.currentThread() else null
    try {
        return block()
    } finally {
        // Restore the interrupted state (if there was one)
        try {
            thread?.interrupt()
        } catch (_: SecurityException) {}
    }
}
