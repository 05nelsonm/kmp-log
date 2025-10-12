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

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal expect class Lock

internal expect inline fun newLock(): Lock

@Suppress("LEAKED_IN_PLACE_LAMBDA", "WRONG_INVOCATION_KIND")
internal inline fun <R> Lock.withLock(block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return withLockImpl(block)
}

internal expect inline fun <R> Lock.withLockImpl(block: () -> R): R
