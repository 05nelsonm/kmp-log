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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.log.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal actual class Lock {

    private companion object {
        // Using Long (64-bit) as it is out of the addressable space for kmp_log_thread_current_uid (32-bit)
        private const val UNLOCKED: Long = Long.MAX_VALUE
    }

    private val lock = AtomicLong(UNLOCKED)
    private val reentries = AtomicInt(0)

    internal fun lock(threadUID: ThreadUID) {
        while (true) {
            when (lock.compareAndExchange(UNLOCKED, threadUID.value)) {
                threadUID.value -> {
                    reentries.incrementAndGet()
                    break
                }
                UNLOCKED -> {
                    reentries.value.let { check(it == 0) { "reentries.value[$it] != 0" } }
                    break
                }
            }
        }
    }

    internal fun unlock(threadUID: ThreadUID) {
        lock.value.let { check(it == threadUID.value) { "lock.value[$it] != threadUID[${threadUID.value}]" } }

        if (reentries.value > 0) {
            reentries.decrementAndGet()
        } else {
            val previous = lock.compareAndExchange(threadUID.value, UNLOCKED)
            check(previous == threadUID.value) { "previous[$previous] != threadUID[${threadUID.value}]" }
        }
    }

    internal value class ThreadUID private constructor(internal val value: Long) {
        @OptIn(ExperimentalForeignApi::class)
        internal constructor(): this(kmp_log_thread_current_uid().toLong())
    }
}

internal actual inline fun newLock(): Lock = Lock()

internal actual inline fun <R> Lock.withLockImpl(block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val threadUID = Lock.ThreadUID()
    lock(threadUID)
    try {
        return block()
    } finally {
        unlock(threadUID)
    }
}
