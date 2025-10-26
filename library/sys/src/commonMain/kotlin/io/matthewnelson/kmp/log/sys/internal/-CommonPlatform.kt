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
@file:Suppress("NOTHING_TO_INLINE", "LocalVariableName")

package io.matthewnelson.kmp.log.sys.internal

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.Log.Level
import io.matthewnelson.kmp.log.sys.SysLog
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// NOTE: Never modify. If so, update SysLog.Companion.UID documentation
internal const val SYS_LOG_UID: String = "io.matthewnelson.kmp.log.sys.SysLog"
internal const val CR: Char = '\r'
internal const val LF: Char = '\n'

internal inline fun ((min: Level) -> SysLog).commonOf(
    min: Level,
): SysLog {
    if (min != SysLog.Debug.min) return this(min)
    return SysLog.Debug
}

internal inline fun SysLog.Companion.commonIsInstalled(): Boolean = Log.Root[UID] != null

// TODO: Move to :log as Log.Util.simpleDomainTag?
internal inline fun SysLog.Companion.commonDomainTag(
    domain: String?,
    tag: String,
): String {
    if (domain == null) return tag
    val sb = StringBuilder(domain.length + 2 + tag.length + 1)
    return commonDomainTag(sb, domain, tag).toString()
}

internal inline fun SysLog.Companion.commonDomainTag(
    sb: StringBuilder,
    domain: String?,
    tag: String,
): StringBuilder = sb.apply {
    if (domain != null) {
        append('[').append(domain).append(']')
    }
    append(tag)
}

internal inline fun SysLog.Companion.commonFormatDateTime(
    month: Int,     // 1-12
    day: Int,       // 1-31
    hours: Int,     // 0-23
    minutes: Int,   // 0-59
    seconds: Int,   // 0-59
    millis: Int,    // 0-999
): CharSequence = StringBuilder(19).apply {
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

// TODO: Move to :log as Log.Util.simpleFormat?
// Returns `null` if empty (no data to log)
internal inline fun SysLog.Companion.commonFormatLogOrNull(
    level: Level,
    domain: String?,
    tag: String,
    msg: String?,
    t: Throwable?,
    dateTime: CharSequence?,
    omitLastNewLine: Boolean,
): CharSequence? {
    val prefix = run {
        var capacity = 0
        if (!dateTime.isNullOrBlank()) {
            capacity += dateTime.length
            capacity++    // ' '
        }
        capacity += 2     // Level
        if (domain != null) {
            capacity += domain.length
            capacity += 2 // []
        }
        capacity += tag.length
        capacity += 2     // ': '
        ++capacity        // For good measure

        if (dateTime != null && dateTime is StringBuilder) {
            dateTime.ensureCapacity(capacity)
            dateTime
        } else {
            StringBuilder(capacity)
        }
    }

    if (!dateTime.isNullOrBlank()) {
        if (prefix != dateTime) prefix.append(dateTime)
        prefix.append(' ')
    }
    prefix.append(level.name.first()).append(' ')
    commonDomainTag(sb = prefix, domain, tag).append(':').append(' ')

    // TODO: Optimize. This is creating a bunch of unnecessary strings
    //  whereby parsing it once in reverse would enable collection of
    //  all the ranges. Can calculate a more accurate capacity because
    //  when a line is found, can strip the end of any whitespace which
    //  would not go into the final log.
    val linesMsg = msg?.lines() ?: emptyList()
    val stack = t?.stackTraceToString()
    val linesStack = stack?.lines() ?: emptyList()

    val sb = run {
        var capacity = msg?.length ?: 0
        if (stack != null) {
            if (capacity != 0) capacity++
            capacity += stack.length
        }
        capacity += (linesMsg.size * prefix.length)
        capacity += (linesStack.size * prefix.length)
        StringBuilder(++capacity)
    }

    linesMsg.forEach { line ->
        if (line.isBlank()) return@forEach
        sb.append(prefix).appendLine(line)
    }
    linesStack.forEach { line ->
        if (line.isBlank()) return@forEach
        sb.append(prefix).appendLine(line)
    }
    if (omitLastNewLine && sb.isNotEmpty()) {
        sb.setLength(sb.length - 1)
    }

    return sb.ifEmpty { null }
}

@OptIn(ExperimentalContracts::class)
@Throws(IllegalArgumentException::class)
internal inline fun SysLog.Companion.commonLogChunk(
    formatted: CharSequence,
    maxLenLog: Int,
    _print: (chunk: String) -> Boolean,
): Boolean {
    contract { callsInPlace(_print, InvocationKind.UNKNOWN) }
    require(maxLenLog > 0) { "maxLenLog must be greater than 0" }

    var len = formatted.length
    while (len > 0 && formatted[len - 1].isWhitespace()) {
        len--
    }

    if (len == 0) return false

    if (len < maxLenLog) {
        @Suppress("ReplaceSubstringWithTake")
        val final = when {
            len == formatted.length -> formatted.toString()
            formatted is StringBuilder -> formatted.apply { setLength(len) }.toString()
            else -> formatted.substring(0, len)
        }
        return _print(final)
    }

    // Need to chunk in blocks of maxLenLog. In order to maximize output,
    // find the last line for that chunk, trim whitespace off the end, then
    // send it. If no lines are found, the last whitespace character is used
    // so the breakpoint is not mid-word. If no whitespace is found to use
    // as the breakpoint, then and only then will the entire chunk be used.
    var iStart = 0
    var iLimit = maxLenLog
    val proxy = object : CharSequence {
        override val length: Int get() = iLimit - iStart
        override fun get(index: Int): Char = formatted[iStart + index]
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = error("unused")
        override fun toString(): String = formatted.substring(iStart, iLimit)
    }

    var somethingWasPrinted = false
    while (iStart < len) {
        var iBreakPoint: Int

        if (iLimit >= len) {
            // Last chunk. Only trim whitespace from end if it's present.
            iLimit = len
            iBreakPoint = proxy.length
        } else {
            // Search for a breakpoint of CR or LF
            iBreakPoint = proxy.indexOfLast { it == CR || it == LF }
            if (iBreakPoint == -1) {
                // No new line to use as breakpoint. Find the last
                // whitespace to use so the breakpoint is not in the
                // middle of a word or something.
                iBreakPoint = proxy.indexOfLast { it.isWhitespace() }

                if (iBreakPoint == -1) {
                    // No whitespace either. Send the whole thing.
                    // indexOfLast for iTrimmed will pop out immediately.
                    iBreakPoint = proxy.length
                }
            }
        }

        val hasBreakPoint = iBreakPoint != proxy.length

        // Trim whitespace from the end
        iLimit = iStart + iBreakPoint
        val iTrimmed = proxy.indexOfLast { !it.isWhitespace() }
        if (iTrimmed != -1) {
            // A chunk with the end trimmed of whitespace was found.
            iLimit = (iStart + iTrimmed + 1)
            if (!_print(proxy.toString())) return false
            somethingWasPrinted = true
        }/* else {
            // Entire chunk was whitespace
        }*/

        iStart += iBreakPoint
        // If there was a breakpoint found, do not want to
        // include the new line or whitespace character at
        // that index in the next chunk. Skip it.
        if (hasBreakPoint) iStart++

        iLimit = iStart + maxLenLog
    }

    return somethingWasPrinted
}
