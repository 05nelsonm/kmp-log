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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE", "WRONG_INVOCATION_KIND")

package io.matthewnelson.kmp.log.internal

import kotlin.concurrent.AtomicInt
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private object TLS { var owner: Any? = null }

private const val UNLOCKED = 0

@OptIn(ExperimentalNativeApi::class)
internal actual class Lock {
    private val lock = AtomicInt(UNLOCKED)
    private val reentries = AtomicInt(UNLOCKED)

    internal fun lock() {
        if (TLS.owner == null) {
            TLS.owner = Any()
        }
        val ownerId = TLS.owner.hashCode()
        check(ownerId != UNLOCKED) { "ownerId == $UNLOCKED" }
        while (true) {
            val previous = lock.compareAndExchange(UNLOCKED, ownerId)
            when (previous) {
                ownerId -> {
                    reentries.incrementAndGet()
                    break
                }
                UNLOCKED -> {
                    check(reentries.value == 0) { "reentries.value != 0" }
                    break
                }
            }
        }
    }

    internal fun unlock() {
        check(TLS.owner != null) { "TLS.owner == null" }
        if (reentries.value > 0) {
            reentries.decrementAndGet()
        } else {
            val ownerId = TLS.owner.hashCode()
            check(ownerId != UNLOCKED) { "ownerId == $UNLOCKED" }
            val previous = lock.compareAndExchange(ownerId, UNLOCKED)
            check(previous == ownerId) { "previous[$previous] != ownerId[$ownerId]" }
            TLS.owner = null
        }
    }
}

internal actual inline fun newLock(): Lock = Lock()

internal actual inline fun <R> Lock.withLockImpl(block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    // Native is weird in that it may cause a null pointer de-reference (sometimes
    // experienced when using coroutines). Forcing localization seems to fix the
    // issues.
    val local = this
    local.lock()
    var threw: Throwable? = null
    val ret = try {
        block()
    } catch (t: Throwable) {
        threw = t
        null
    } finally {
        local.unlock()
    }
    threw?.let { throw it }
    local.hashCode()
    @Suppress("UNCHECKED_CAST")
    return ret as R
}
