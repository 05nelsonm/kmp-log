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
@file:Suppress("LEAKED_IN_PLACE_LAMBDA", "NOTHING_TO_INLINE", "WRONG_INVOCATION_KIND")

package io.matthewnelson.kmp.log.file.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext

internal object CurrentThread

internal inline fun <T> CurrentThread.uninterruptedRunBlocking(
    context: CoroutineContext,
    noinline block: suspend CoroutineScope.() -> T,
): T = uninterrupted { runBlocking(context, block) }

@OptIn(ExperimentalContracts::class)
internal inline fun <T> CurrentThread.uninterrupted(block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return uninterruptedImpl(block)
}

internal expect fun CurrentThread.id(): Long

internal expect inline fun <T> CurrentThread.uninterruptedImpl(block: () -> T): T
