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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.log.Log

internal expect inline fun Log.Root.isDesktop(): Boolean

internal expect fun Log.Root.now(): CharSequence

internal expect fun Log.Root.pid(): Int

internal inline fun Throwable.isCancellationException(): Boolean {
    if (this is kotlin.coroutines.cancellation.CancellationException) return true
    if (this is kotlinx.coroutines.CancellationException) return true
    return false
}
