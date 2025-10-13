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
package io.matthewnelson.kmp.log.sys.internal.js

import kotlin.js.JsName

@JsName("Date")
internal external class JsDate() {
    fun getDate(): Int          // 0-31
    fun getMonth(): Int         // 0-11
    fun getHours(): Int         // 0-23
    fun getMinutes(): Int       // 0-59
    fun getSeconds(): Int       // 0-59
    fun getMilliseconds(): Int  // 0-999
}

// 10-12 13:11:09.538
internal fun JsDate.formatMMddHHmmssSSS(): String {
    var dateTime = ""
    run {
        val month = getMonth() + 1
        if (month <= 9) dateTime += '0'
        dateTime += month
    }
    dateTime += '-'
    run {
        val day = getDate()
        if (day <= 9) dateTime += '0'
        dateTime += day
    }
    dateTime += ' '
    run {
        val hours = getHours()
        if (hours <= 9) dateTime += '0'
        dateTime += hours
    }
    dateTime += ':'
    run {
        val minutes = getMinutes()
        if (minutes <= 9) dateTime += '0'
        dateTime += minutes
    }
    dateTime += ':'
    run {
        val seconds = getSeconds()
        if (seconds <= 9) dateTime += '0'
        dateTime += seconds
    }
    dateTime += '.'
    run {
        val millis = getMilliseconds()
        if (millis <= 99) dateTime += '0'
        if (millis <=  9) dateTime += '0'
        dateTime += millis
    }
    return dateTime
}
