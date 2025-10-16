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

package io.matthewnelson.kmp.log.sys.internal

import io.matthewnelson.kmp.log.Log.Level
import io.matthewnelson.kmp.log.internal.kmp_log_local_date_time
import io.matthewnelson.kmp.log.sys.SysLog
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.FILE
import platform.posix.fprintf
import platform.posix.stderr
import platform.posix.stdout
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal fun SysLog.Default.nativeDateTime(): CharSequence? {
    val dateTime = IntArray(7)
    @Suppress("RemoveRedundantCallsOfConversionMethods")
    @OptIn(ExperimentalForeignApi::class)
    val ret = dateTime.usePinned { pinned ->
        kmp_log_local_date_time(date_time = pinned.addressOf(0))
    }.toInt()
    if (ret != 0) return null
    return commonFormatDateTime(
        // year = dateTime[0],
        month   = dateTime[1],
        day     = dateTime[2],
        hours   = dateTime[3],
        minutes = dateTime[4],
        seconds = dateTime[5],
        millis  = dateTime[6],
    )
}

// NOTE: Cannot be utilized for androidNative because
// /proc/self/fd/{0/1/2} point to /dev/null
@OptIn(ExperimentalContracts::class, ExperimentalForeignApi::class)
internal inline fun SysLog.Default.nativeLogPrint(
    level: Level,
    domain: String?,
    tag: String,
    msg: String?,
    t: Throwable?,
    _dateTime: () -> CharSequence? = ::nativeDateTime,
): Boolean {
    contract { callsInPlace(_dateTime, InvocationKind.AT_MOST_ONCE) }
    val stdio = stdioOrNull(level) ?: return false
    val formatted = run {
        val dateTime = _dateTime()
        commonFormatLog(level, domain, tag, msg, t, dateTime, omitLastNewLine = false)
    }.toString()
    return fprintf(stdio, formatted) > 0
}

// NOTE: Cannot be utilized for androidNative because
// /proc/self/fd/{0/1/2} point to /dev/null
@OptIn(ExperimentalForeignApi::class)
internal inline fun SysLog.Default.nativeIsLoggable(level: Level): Boolean = stdioOrNull(level) != null

@OptIn(ExperimentalForeignApi::class)
internal inline fun SysLog.Default.stdioOrNull(level: Level): CPointer<FILE>? = when (level) {
    Level.Verbose,
    Level.Debug,
    Level.Info,
    Level.Warn -> stdout
    Level.Error,
    Level.Fatal -> stderr
}
