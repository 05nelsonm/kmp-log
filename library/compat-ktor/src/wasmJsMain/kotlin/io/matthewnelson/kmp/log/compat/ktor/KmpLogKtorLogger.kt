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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.log.compat.ktor

import io.ktor.util.logging.LogLevel
import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.compat.ktor.internal.jsWasmJsOf
import io.matthewnelson.kmp.log.compat.ktor.internal.nonJvmDebug
import io.matthewnelson.kmp.log.compat.ktor.internal.nonJvmEquals
import io.matthewnelson.kmp.log.compat.ktor.internal.nonJvmError
import io.matthewnelson.kmp.log.compat.ktor.internal.nonJvmFindMinLevelOrNull
import io.matthewnelson.kmp.log.compat.ktor.internal.nonJvmHashCode
import io.matthewnelson.kmp.log.compat.ktor.internal.nonJvmInfo
import io.matthewnelson.kmp.log.compat.ktor.internal.nonJvmToString
import io.matthewnelson.kmp.log.compat.ktor.internal.nonJvmTrace
import io.matthewnelson.kmp.log.compat.ktor.internal.nonJvmWarn

// wasmJs
public actual abstract class KmpLogKtorLogger private constructor(
    public actual val delegate: Log.Logger,
): io.ktor.util.logging.Logger {

    public actual companion object Compat {

        public actual fun of(
            logger: Log.Logger,
        ): KmpLogKtorLogger = ::factory.jsWasmJsOf(logger)

        private fun factory(delegate: Log.Logger): KmpLogKtorLogger = object : KmpLogKtorLogger(delegate) {
            override val level: LogLevel get() = when (nonJvmFindMinLevelOrNull()) {
                null -> LogLevel.NONE
                Log.Level.Verbose -> LogLevel.TRACE
                Log.Level.Debug -> LogLevel.DEBUG
                Log.Level.Info -> LogLevel.INFO
                Log.Level.Warn -> LogLevel.WARN
                Log.Level.Error -> LogLevel.ERROR
                Log.Level.Fatal -> LogLevel.NONE
            }
            override fun trace(message: String) { nonJvmTrace(message, t = null) }
            override fun trace(message: String, cause: Throwable) { nonJvmTrace(message, cause) }
            override fun debug(message: String) { nonJvmDebug(message, t = null) }
            override fun debug(message: String, cause: Throwable) { nonJvmDebug(message, cause) }
            override fun info(message: String) { nonJvmInfo(message, t = null) }
            override fun info(message: String, cause: Throwable) { nonJvmInfo(message, cause) }
            override fun warn(message: String) { nonJvmWarn(message, t = null) }
            override fun warn(message: String, cause: Throwable) { nonJvmWarn(message, cause) }
            override fun error(message: String) { nonJvmError(message, t = null) }
            override fun error(message: String, cause: Throwable) { nonJvmError(message, cause) }
        }
    }

    /** @suppress */
    public actual final override fun equals(other: Any?): Boolean = nonJvmEquals(other)
    /** @suppress */
    public actual final override fun hashCode(): Int = nonJvmHashCode()
    /** @suppress */
    public actual final override fun toString(): String = nonJvmToString()
}
