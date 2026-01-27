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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "FunctionName", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.log.file.FileLog

@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias AtomicLong = java.util.concurrent.atomic.AtomicLong
@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias AtomicRef<T> = java.util.concurrent.atomic.AtomicReference<T>

internal actual inline fun FileLog.Companion._atomic(initial: Long): AtomicLong = AtomicLong(initial)
internal actual inline fun <T> FileLog.Companion._atomicRef(initial: T): AtomicRef<T> = AtomicRef(initial)

internal actual inline fun AtomicLong._get(): Long = get()
internal actual inline fun AtomicLong._increment() { incrementAndGet() }
internal actual inline fun AtomicLong._decrement() { decrementAndGet() }

internal actual inline fun <T> AtomicRef<T>._get(): T = get()
internal actual inline fun <T> AtomicRef<T>._set(new: T) { set(new) }
internal actual inline fun <T> AtomicRef<T>._getAndSet(new: T): T = getAndSet(new)
internal actual inline fun <T> AtomicRef<T>._compareAndSet(expected: T, new: T): Boolean {
    return compareAndSet(expected, new)
}
