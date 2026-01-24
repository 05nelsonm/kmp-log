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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "UNUSED")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.log.file.FileLog

internal expect class AtomicLong
internal expect class AtomicRef<T>

internal expect inline fun FileLog.Companion.atomicLong(initialValue: Long): AtomicLong
internal expect inline fun <T> FileLog.Companion.atomicRef(initialValue: T): AtomicRef<T>

internal expect inline fun AtomicLong.valueGet(): Long
internal expect inline fun AtomicLong.valueIncrement()
internal expect inline fun AtomicLong.valueDecrement()

internal expect inline fun <T> AtomicRef<T>.valueGet(): T
internal expect inline fun <T> AtomicRef<T>.valueGetAndSet(newValue: T): T
