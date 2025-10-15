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

package io.matthewnelson.kmp.log.internal

import io.matthewnelson.kmp.log.Log
import kotlin.system.exitProcess

private const val SIGABRT = 6

private val ANDROID_LOG_WTF by lazy {
    try {
        val clazz = Class.forName("android.util.Log")

        // Will throw exception if AndroidUnitTest
        clazz
            .getMethod("isLoggable", String::class.java, Int::class.javaPrimitiveType)
            .invoke(null, null, 7)

        clazz.getMethod("wtf", String::class.java, String::class.java)
    } catch (_: Throwable) {
        null
    }
}

internal actual val ABORTER_ACCEPTS_MESSAGE: Boolean = ANDROID_LOG_WTF != null

internal actual inline fun Log.AbortHandler.doAbort(t: Throwable?): Boolean {
    val wtf = ANDROID_LOG_WTF
    return if (wtf != null) {
        var msg = t?.stackTraceToString()?.trimEnd()
        if (msg != null && msg.length > 4_000) msg = msg.take(4_000)
        (wtf.invoke(null, null, msg) as Int) > 0
    } else {

        try {
            Runtime.getRuntime().halt(128 + SIGABRT)
        } catch (_: Throwable) {
            // SecurityException
            exitProcess(128 + SIGABRT)
        }
        true
    }
}
