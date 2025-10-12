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

import kotlin.concurrent.AtomicInt
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias Lock = AtomicInt

private const val UNLOCKED = 0

internal actual inline fun newLock(): Lock = Lock(UNLOCKED)

internal actual inline fun <R> Lock.withLockImpl(block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val lock = this
    var any: Any
    do {
        any = Any()
    } while (any.hashCode() == UNLOCKED)

    while (true) {
        if (lock.compareAndSet(UNLOCKED, any.hashCode())) break
    }

    return try {
        block()
    } finally {
        lock.compareAndSet(any.hashCode(), UNLOCKED)
    }
}
