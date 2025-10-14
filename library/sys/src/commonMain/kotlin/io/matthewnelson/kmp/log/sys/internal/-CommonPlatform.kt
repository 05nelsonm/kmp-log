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

import io.matthewnelson.kmp.log.Log.Level
import io.matthewnelson.kmp.log.sys.SysLog
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// NOTE: Never modify. If so, update SysLog.Default.UID documentation.
internal const val SYS_LOG_UID: String = "io.matthewnelson.kmp.log.sys.SysLog"

@Suppress("RedundantCompanionReference")
internal inline fun ((min: Level) -> SysLog).commonOf(
    min: Level,
): SysLog {
    if (min != SysLog.Default.min) return this(min)
    return SysLog.Default
}

// TODO: Move to :log as Log.Util.simpleDomainTag?
internal inline fun SysLog.Default.commonDomainTag(
    domain: String?,
    tag: String,
): String {
    if (domain == null) return tag
    var capacity = domain.length + 2 + tag.length
    val sb = StringBuilder(++capacity)
    return commonDomainTag(sb, domain, tag).toString()
}

internal inline fun SysLog.Default.commonDomainTag(
    sb: StringBuilder,
    domain: String?,
    tag: String,
): StringBuilder = sb.apply {
    if (domain != null) {
        append('[').append(domain).append(']')
    }
    append(tag)
}

// TODO: Move to :log as Log.Util.simpleFormat?
internal inline fun SysLog.Default.commonFormat(
    level: Level,
    domain: String?,
    tag: String,
    msg: String?,
    t: Throwable?,
    dateTime: String?,
    omitLastNewLine: Boolean,
): CharSequence {
    val prefix = run {
        var capacity = 0
        if (!dateTime.isNullOrBlank()) {
            capacity += dateTime.length
            capacity++
        }
        capacity += 2
        if (domain != null) {
            capacity += domain.length
            capacity += 2
        }
        capacity += tag.length
        capacity += 2
        StringBuilder(++capacity)
    }

    if (!dateTime.isNullOrBlank()) {
        prefix.append(dateTime).append(' ')
    }
    prefix.append(level.name.first()).append(' ')
    commonDomainTag(sb = prefix, domain, tag).append(':').append(' ')

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
        sb.append(prefix).appendLine(line)
    }
    linesStack.forEach { line ->
        if (line.isBlank()) return@forEach
        sb.append(prefix).appendLine(line)
    }
    if (omitLastNewLine && sb.isNotEmpty()) {
        sb.setLength(sb.length - 1)
    }

    return sb
}

@OptIn(ExperimentalContracts::class)
@Throws(IllegalArgumentException::class)
internal inline fun SysLog.Default.commonLogChunk(
    formatted: CharSequence,
    maxLenLog: Int,
    _print: (chunk: String) -> Boolean,
): Boolean {
    contract { callsInPlace(_print, InvocationKind.UNKNOWN) }
    require(maxLenLog >= 1_000) { "maxLen[$maxLenLog] < 1_000" }

    if (formatted.length < maxLenLog) {
        return _print(formatted.toString())
    }

    // TODO: Implement chunking
    return _print(formatted.toString())
}
