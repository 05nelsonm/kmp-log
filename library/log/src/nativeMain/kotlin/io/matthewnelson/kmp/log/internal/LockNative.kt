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
        // Using Long as it is out of the addressable space for threadUID (32-bit)
        private const val UNLOCKED: Long = Long.MAX_VALUE
    }

    private val lock = AtomicLong(UNLOCKED)
    private val reentries = AtomicInt(0)

    @Deprecated("Use Lock.withLock", level = DeprecationLevel.ERROR)
    internal fun lock(threadUID: Int) {
        val threadUID64 = threadUID.toLong()
        while (true) {
            when (lock.compareAndExchange(UNLOCKED, threadUID64)) {
                threadUID64 -> {
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

    @Deprecated("Use Lock.withLock", level = DeprecationLevel.ERROR)
    internal fun unlock(threadUID: Int) {
        val threadUID64 = threadUID.toLong()
        lock.value.let { check(it == threadUID64) { "lock.value[$it] != threadUID[$threadUID]" } }

        if (reentries.value > 0) {
            reentries.decrementAndGet()
        } else {
            val previous = lock.compareAndExchange(threadUID64, UNLOCKED)
            check(previous == threadUID64) { "previous[$previous] != threadUID[$threadUID]" }
        }
    }
}

internal actual inline fun newLock(): Lock = Lock()

@Suppress("DEPRECATION_ERROR")
internal actual inline fun <R> Lock.withLockImpl(block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    @OptIn(ExperimentalForeignApi::class)
    val threadUID = kmp_log_thread_current_uid().toInt()
    lock(threadUID)
    try {
        return block()
    } finally {
        unlock(threadUID)
    }
}
