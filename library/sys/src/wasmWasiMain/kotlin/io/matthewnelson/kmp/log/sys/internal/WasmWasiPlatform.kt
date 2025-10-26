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
package io.matthewnelson.kmp.log.sys.internal

import io.matthewnelson.kmp.log.sys.SysLog
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// The only timezone available for Wasi is UTC, otherwise one must add a
// massive dependency. The choice was to not do that for something like
// logging, and to instead increase the supported KotlinVersion for Wasi
// from KOTLIN_1_9 to KOTLIN_2_1 and use the stdlib's Instant.
// See: https://github.com/Kotlin/kotlinx-datetime/tree/v0.6.2#note-about-time-zones-in-wasmwasi
internal fun SysLog.Companion.wasiDateTime(): CharSequence? {
    // 2023-01-02T23:40:57.120Z
    val iso8601 = try {
        @OptIn(ExperimentalTime::class)
        Clock.System.now().toString()
    } catch (_: Throwable) {
        // Can throw WasiError
        return null
    }
    val sb = StringBuilder(20)
    var iDot = Int.MIN_VALUE
    for (i in 5 until iso8601.length) {
        // Only allow 3 units of millisecond precision.
        if (i == iDot + 4) break
        val c = iso8601[i]
        if (c == '.') iDot = i
        if (c == 'T') sb.append(' ') else sb.append(c)
    }
    // Keep trailing Z, indicative of UTC.
    sb.append('Z')
    return sb
}
