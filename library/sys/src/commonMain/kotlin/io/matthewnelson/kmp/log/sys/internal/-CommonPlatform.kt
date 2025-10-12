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

// NOTE: Never modify. If so, update SysLog.Companion.UID documentation.
internal const val SYS_LOG_UID: String = "io.matthewnelson.kmp.log.sys.SysLog"

// TODO: Move to :log as Log.Util.simpleDomainTag?
internal inline fun SysLog.Companion.commonDomainTag(
    domain: String?,
    tag: String,
): String {
    // TODO: Should domain be "none" so that all logs have [something] and
    //  can more easily be parsed?
    if (domain == null) return tag
    return "[$domain]$tag"
}

// TODO: Move to :log as Log.Util.simpleFormat?
//  Would need a time source, so...
internal inline fun SysLog.Companion.commonFormat(
    level: Level,
    domain: String?,
    tag: String,
    msg: String?,
    t: Throwable?,
    dateTime: String?,
    omitLastNewLine: Boolean,
): String {
    var prefix = ""
    if (!dateTime.isNullOrBlank()) {
        prefix = dateTime
        prefix += " "
    }
    prefix += level.name.first()
    prefix += ' '
    prefix += commonDomainTag(domain, tag)
    prefix += ": "

    return StringBuilder().apply {
        msg?.lines()?.forEach { line ->
            append(prefix).appendLine(line)
        }
        t?.stackTraceToString()?.lines()?.forEach { line ->
            if (line.isBlank()) return@forEach
            append(prefix).appendLine(line)
        }
        if (omitLastNewLine) {
            setLength(length - 1)
        }
    }.toString()
}
