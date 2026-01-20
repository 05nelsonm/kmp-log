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
package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.file.FileLog
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
internal inline fun FileLog.Companion.format(
    time: CharSequence,
    pid: Int,
    tid: Long,
    level: Log.Level,
    domain: String?,
    tag: String,
    msg: String?,
    t: Throwable?,
    trace: (capacity: Int, length: Int) -> Unit = { _, _ -> },
): CharSequence? {
    contract { callsInPlace(trace, InvocationKind.UNKNOWN) }

    val linesM = msg?.lines() ?: emptyList()
    val linesT = t?.stackTraceToString()?.lines() ?: emptyList()

    var capacity = 0
    var lines = 0
    linesM.forEach { line ->
        if (line.isEmpty()) return@forEach
        lines++
        capacity += line.length
        capacity++
    }
    linesT.forEach { line ->
        if (line.isEmpty()) return@forEach
        lines++
        capacity += line.length
        capacity++
    }
    if (capacity == 0) return null

    val prefix = formatPrefix(time, pid, tid, level, domain, tag, trace)
    capacity += (lines * prefix.length)

    val sb = StringBuilder(capacity)
    linesM.forEach { line ->
        if (line.isEmpty()) return@forEach
        sb.append(prefix).appendLine(line)
    }
    linesT.forEach { line ->
        if (line.isEmpty()) return@forEach
        sb.append(prefix).appendLine(line)
    }

    trace(capacity, sb.length)
    return sb
}

@OptIn(ExperimentalContracts::class)
internal inline fun FileLog.Companion.formatPrefix(
    time: CharSequence,
    pid: Int,
    tid: Long,
    level: Log.Level,
    domain: String?,
    tag: String,
    trace: (capacity: Int, length: Int) -> Unit = { _, _ -> },
): CharSequence {
    contract { callsInPlace(trace, InvocationKind.EXACTLY_ONCE) }

    var capacity = time.length
    capacity++      // ' '
    capacity++      // Level.name first character
    capacity++      // ' '
    capacity += 7   // pid
    capacity++      // ' '
    capacity += 7   // tid
    capacity++      // ' '
    if (domain != null) {
        capacity++  // '['
        capacity += domain.length
        capacity++  // ']'
    }
    capacity += tag.length
    capacity++      // ':'
    capacity++      // ' '
    val sb = StringBuilder(capacity)

    sb.append(time)
    sb.append(' ')
    sb.append(level.name.first())
    sb.append(' ')

    if (pid <= 0) {
        sb.append("unknown")
    } else {
        if (pid <= 999_999) sb.append('0')
        if (pid <=  99_999) sb.append('0')
        if (pid <=   9_999) sb.append('0')
        if (pid <=     999) sb.append('0')
        if (pid <=      99) sb.append('0')
        if (pid <=       9) sb.append('0')
        sb.append(pid)
    }

    sb.append(' ')

    if (tid < 0L) {
        sb.append("unknown")
    } else {
        if (tid <= 999_999L) sb.append('0')
        if (tid <=  99_999L) sb.append('0')
        if (tid <=   9_999L) sb.append('0')
        if (tid <=     999L) sb.append('0')
        if (tid <=      99L) sb.append('0')
        if (tid <=       9L) sb.append('0')
        sb.append(tid)
    }

    sb.append(' ')

    if (domain != null) {
        sb.append('[')
        sb.append(domain)
        sb.append(']')
    }

    sb.append(tag)
    sb.append(':')
    sb.append(' ')

    trace(capacity, sb.length)
    return sb
}
