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

import io.matthewnelson.kmp.file.ANDROID
import io.matthewnelson.kmp.log.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal actual inline fun Log.Root.isDesktop(): Boolean = ANDROID.SDK_INT == null

internal actual fun Log.Root.now(): String {
    val now = Date(System.currentTimeMillis())
    return LOG_TIME_FORMAT.format(now)
}

internal actual fun Log.Root.pid(): Int = JVM_PID

private val LOG_TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ENGLISH)

// https://github.com/05nelsonm/kmp-process/blob/master/library/process/src/jvmMain/kotlin/io/matthewnelson/kmp/process/internal/-PID.kt
private val JVM_PID: Int by lazy {
    if (ANDROID.SDK_INT != null) {
        return@lazy Class.forName("android.os.Process")
            .getMethod("myPid")
            .invoke(null) as Int
    }

    // Java 9
    try {
        val clazz = Class.forName("java.lang.ProcessHandle")
        val mCurrent = clazz.getMethod("current")
        val mPid = clazz.getMethod("pid")
        if (mCurrent != null && mPid != null) {
            val current = mCurrent.invoke(null)
            if (current != null) {
                return@lazy (mPid.invoke(current) as Long).toInt()
            }
        }
    } catch (_: Throwable) {}

    // Java 10
    try {
        val mGetPid = java.lang.management.RuntimeMXBean::class.java
            .getMethod("getPid")
        if (mGetPid != null) {
            val mxBean = java.lang.management.ManagementFactory
                .getRuntimeMXBean()
            return@lazy (mGetPid.invoke(mxBean) as Long).toInt()
        }
    } catch (_: Throwable) {}

    // Java 8
    try {
        return@lazy java.lang.management.ManagementFactory
            .getRuntimeMXBean()
            .name
            .split('@')[0]
            .toInt()
    } catch (_: Throwable) {}

    // Unknown
    -1
}
