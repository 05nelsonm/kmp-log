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

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.internal.KMP_LOG_LOCAL_DATE_TIME_SIZE
import io.matthewnelson.kmp.log.internal.kmp_log_local_date_time
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

private const val ZERO_TIME = "00-00 00:00:00.000"

internal actual fun Log.Root.now(): String {
    @OptIn(ExperimentalForeignApi::class)
    val dateTime = IntArray(KMP_LOG_LOCAL_DATE_TIME_SIZE)
    @OptIn(ExperimentalForeignApi::class)
    val ret = dateTime.usePinned { pinned ->
        kmp_log_local_date_time(date_time = pinned.addressOf(0))
    }.toInt()
    if (ret != 0) return ZERO_TIME

//    val year    = dateTime[0]
    val month   = dateTime[1]
    val day     = dateTime[2]
    val hours   = dateTime[3]
    val minutes = dateTime[4]
    val seconds = dateTime[5]
    val millis  = dateTime[6]

    return StringBuilder(ZERO_TIME.length).apply {
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
    }.toString()
}
