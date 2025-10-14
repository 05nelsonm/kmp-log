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

import io.matthewnelson.kmp.log.sys.SysLog
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

private const val MAX_LEN_LOG: Int = 4000
private const val MAX_LEN_TAG: Int = 23

internal inline fun SysLog.Default.androidDomainTag(
    DEVICE_SDK_INT: Int,
    domain: String?,
    tag: String,
): String = when {
    DEVICE_SDK_INT >= 26 -> commonDomainTag(domain, tag)
    tag.length <= MAX_LEN_TAG -> tag
    else -> tag.take(MAX_LEN_TAG)
}

@OptIn(ExperimentalContracts::class)
internal inline fun SysLog.Default.androidLogChunk(
    msg: String?,
    t: Throwable?,
    _print: (chunk: String) -> Boolean,
): Boolean {
    contract { callsInPlace(_print, InvocationKind.UNKNOWN) }
    val stack = t?.stackTraceToString()

    val sb = run {
        var capacity = msg?.length ?: 0
        if (stack != null) {
            if (capacity != 0) capacity++
            capacity += stack.length
        }
        StringBuilder(++capacity)
    }

    if (msg != null) sb.append(msg)
    if (stack != null) {
        if (sb.isNotEmpty()) sb.appendLine()
        sb.append(stack)
    }

    // trim
    while (sb.isNotEmpty() && sb.last().isWhitespace()) {
        sb.setLength(sb.length - 1)
    }

    return commonLogChunk(sb, MAX_LEN_LOG, _print)
}
