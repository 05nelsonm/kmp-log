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
@file:Suppress("LocalVariableName", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.log.internal

import io.matthewnelson.kmp.log.Log
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import platform.posix.RTLD_NEXT
import platform.posix.abort
import platform.posix.dlsym

// Normally one would not want to hold onto a function pointer reference
// statically, but it's from glibc which is not going to be hot reloaded
// or anything w/o this process terminating, so.
//
// Available for API 30+
// include/android/log.h
@OptIn(ExperimentalForeignApi::class)
private val ANDROID_LOG_CALL_ABORTER by lazy {
    val ptr = dlsym(RTLD_NEXT, "__android_log_call_aborter")
        ?: return@lazy null

    @Suppress("UNCHECKED_CAST")
    ptr as CPointer<CFunction<(
        __abort_message: CValuesRef<ByteVar>?,
    ) -> Unit>>
}

@OptIn(ExperimentalForeignApi::class)
internal actual inline fun Log.AbortHandler.aborterAcceptsMessages(): Boolean = ANDROID_LOG_CALL_ABORTER != null

@OptIn(ExperimentalForeignApi::class)
internal actual inline fun Log.AbortHandler.doAbort(t: Throwable?): Boolean {
    val ___android_log_call_aborter = ANDROID_LOG_CALL_ABORTER
    if (___android_log_call_aborter == null) {
        abort()
        return true
    }
    var msg = t?.stackTraceToString()?.trimEnd()
    if (msg != null && msg.length > 4_000) msg = msg.take(4_000)
    ___android_log_call_aborter.invoke(msg?.cstr)
    return true
}
