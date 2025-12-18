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
@file:Suppress("NOTHING_TO_INLINE", "RedundantCompanionReference")

package io.matthewnelson.kmp.log.compat.ktor.internal

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.compat.ktor.KmpLogKtorLogger

internal inline fun KmpLogKtorLogger.nonJvmFindMinLevelOrNull(): Log.Level? {
    Log.Root.installedLevels().forEach { level ->
        if (delegate.isLoggable(level)) return level
    }
    return null
}

internal inline fun KmpLogKtorLogger.nonJvmTrace(msg: String?, t: Throwable?) { delegate.v(t, msg) }
internal inline fun KmpLogKtorLogger.nonJvmDebug(msg: String?, t: Throwable?) { delegate.d(t, msg) }
internal inline fun KmpLogKtorLogger.nonJvmInfo(msg: String?, t: Throwable?) { delegate.i(t, msg) }
internal inline fun KmpLogKtorLogger.nonJvmWarn(msg: String?, t: Throwable?) { delegate.w(t, msg) }
internal inline fun KmpLogKtorLogger.nonJvmError(msg: String?, t: Throwable?) { delegate.e(t, msg) }

internal inline fun KmpLogKtorLogger.nonJvmEquals(other: Any?): Boolean {
    if (other !is KmpLogKtorLogger) return false
    return other.delegate == this.delegate
}
internal inline fun KmpLogKtorLogger.nonJvmHashCode(): Int {
    var result = 17
    result = result * 31 + this::class.hashCode()
    result = result * 31 + delegate.hashCode()
    return result
}
internal inline fun KmpLogKtorLogger.nonJvmToString(): String {
    return delegate.toString()
        .replaceBefore('[', "KmpLogKtorLogger")
        .replaceAfterLast('@', hashCode().toString())
}
