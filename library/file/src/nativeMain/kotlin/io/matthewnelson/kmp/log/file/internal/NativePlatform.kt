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
@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.log.file.FileLog
import io.matthewnelson.kmp.log.internal.KMP_LOG_LOCAL_DATE_TIME_SIZE
import io.matthewnelson.kmp.log.internal.kmp_log_local_date_time
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.getpid

private const val ZERO_TIME_YEAR_NO = "00-00 00:00:00.000"
private const val ZERO_TIME_YEAR_YES = "0000-$ZERO_TIME_YEAR_NO"

internal actual fun FileLog.Companion.now(omitYear: Boolean): CharSequence {
    @OptIn(ExperimentalForeignApi::class)
    val dateTime = IntArray(KMP_LOG_LOCAL_DATE_TIME_SIZE)
    @OptIn(ExperimentalForeignApi::class)
    val ret = dateTime.usePinned { pinned ->
        kmp_log_local_date_time(date_time = pinned.addressOf(0))
    }.toInt()
    if (ret != 0) return if (omitYear) ZERO_TIME_YEAR_NO else ZERO_TIME_YEAR_YES

    val year    = dateTime[0]
    val month   = dateTime[1]
    val day     = dateTime[2]
    val hours   = dateTime[3]
    val minutes = dateTime[4]
    val seconds = dateTime[5]
    val millis  = dateTime[6]

    return StringBuilder((if (omitYear) ZERO_TIME_YEAR_NO else ZERO_TIME_YEAR_YES).length).apply {
        if (!omitYear) {
            append(year)
            append('-')
        }
        if (month <= 9) append('0')
        append(month)
        append('-')
        if (day <= 9) append('0')
        append(day)
        append(' ')
        if (hours <= 9) append('0')
        append(hours)
        append(':')
        if (minutes <= 9) append('0')
        append(minutes)
        append(':')
        if (seconds <= 9) append('0')
        append(seconds)
        append('.')
        if (millis <= 99) append('0')
        if (millis <= 9) append('0')
        append(millis)
    }
}

internal actual fun FileLog.Companion.pid(): Int = getpid()
