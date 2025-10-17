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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.log.compat.slf4j

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.Log.Level
import org.slf4j.Marker
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Convert [Log.Logger] to [org.slf4j.Logger].
 *
 * **NOTE:** All [Marker] are ignored.
 *
 * @see [of]
 * @see [asSLF4JLogger]
 * */
public class KmpLogSLF4JLogger private constructor(
    @JvmField
    public val delegate: Log.Logger,
): org.slf4j.Logger {

    public companion object Compat {

        /**
         * An alias for [of].
         * */
        @JvmStatic
        public inline fun Log.Logger.asSLF4JLogger(): KmpLogSLF4JLogger = of(this)

        /**
         * Creates a new [KmpLogSLF4JLogger] instance.
         * */
        @JvmStatic
        public fun of(
            logger: Log.Logger,
        ): KmpLogSLF4JLogger {
            // TODO: Cache instances?
            return KmpLogSLF4JLogger(logger)
        }

        /**
         * So [KmpLogSLF4JLogger] can be used in `:compat-ktor` as a `typealias` for `KmpLogKtorLogger`.
         * @suppress
         * */
        @JvmStatic
        public inline fun Log.Logger.asKtorLogger(): KmpLogSLF4JLogger = of(this)
    }

    private val _name = delegate.domain?.let { "[$it]${delegate.tag}" } ?: delegate.tag
    public override fun getName(): String = _name

    // TRACE
    public override fun isTraceEnabled(): Boolean = delegate.isLoggable(Level.Verbose)
    public override fun isTraceEnabled(marker: Marker?): Boolean = isTraceEnabled()

    public override fun trace(message: String) { delegate.v(message, t = null) }
    public override fun trace(message: String, cause: Throwable) { delegate.v(message, cause) }
    public override fun trace(marker: Marker?, message: String?) { delegate.v(message, t = null) }
    public override fun trace(marker: Marker?, message: String?, cause: Throwable?) { delegate.v(message, cause) }

    public override fun trace(format: String?, arg: Any?) {
        format.log(Level.Verbose) { java.lang.String.format(it, arg) }
    }
    public override fun trace(format: String?, arg1: Any?, arg2: Any?) {
        format.log(Level.Verbose) { java.lang.String.format(it, arg1, arg2) }
    }
    public override fun trace(format: String?, vararg arguments: Any?) {
        format.log(Level.Verbose) { java.lang.String.format(it, arguments) }
    }

    public override fun trace(marker: Marker?, format: String?, arg: Any?) {
        format.log(Level.Verbose) { java.lang.String.format(it, arg) }
    }
    public override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        format.log(Level.Verbose) { java.lang.String.format(it, arg1, arg2) }
    }
    public override fun trace(marker: Marker?, format: String?, vararg arguments: Any?) {
        format.log(Level.Verbose) { java.lang.String.format(it, arguments) }
    }

    // DEBUG
    public override fun isDebugEnabled(): Boolean = delegate.isLoggable(Level.Debug)
    public override fun isDebugEnabled(marker: Marker?): Boolean = isDebugEnabled()

    public override fun debug(message: String) { delegate.d(message, t = null) }
    public override fun debug(message: String, cause: Throwable) { delegate.d(message, cause) }
    public override fun debug(marker: Marker?, message: String?) { delegate.d(message, t = null) }
    public override fun debug(marker: Marker?, message: String?, cause: Throwable?) { delegate.d(message, cause) }

    public override fun debug(format: String?, arg: Any?) {
        format.log(Level.Debug) { java.lang.String.format(it, arg) }
    }
    public override fun debug(format: String?, arg1: Any?, arg2: Any?) {
        format.log(Level.Debug) { java.lang.String.format(it, arg1, arg2) }
    }
    public override fun debug(format: String?, vararg arguments: Any?) {
        format.log(Level.Debug) { java.lang.String.format(it, arguments) }
    }

    public override fun debug(marker: Marker?, format: String?, arg: Any?) {
        format.log(Level.Debug) { java.lang.String.format(it, arg) }
    }
    public override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        format.log(Level.Debug) { java.lang.String.format(it, arg1, arg2) }
    }
    public override fun debug(marker: Marker?, format: String?, vararg arguments: Any?) {
        format.log(Level.Debug) { java.lang.String.format(it, arguments) }
    }

    // INFO
    public override fun isInfoEnabled(): Boolean = delegate.isLoggable(Level.Info)
    public override fun isInfoEnabled(marker: Marker?): Boolean = isInfoEnabled()

    public override fun info(message: String) { delegate.i(message, t = null) }
    public override fun info(message: String, cause: Throwable) { delegate.i(message, cause) }
    public override fun info(marker: Marker?, message: String?) { delegate.i(message, t = null) }
    public override fun info(marker: Marker?, message: String?, cause: Throwable?) { delegate.i(message, cause) }

    public override fun info(format: String?, arg: Any?) {
        format.log(Level.Info) { java.lang.String.format(it, arg) }
    }
    public override fun info(format: String?, arg1: Any?, arg2: Any?) {
        format.log(Level.Info) { java.lang.String.format(it, arg1, arg2) }
    }
    public override fun info(format: String?, vararg arguments: Any?) {
        format.log(Level.Info) { java.lang.String.format(it, arguments) }
    }

    public override fun info(marker: Marker?, format: String?, arg: Any?) {
        format.log(Level.Info) { java.lang.String.format(it, arg) }
    }
    public override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        format.log(Level.Info) { java.lang.String.format(it, arg1, arg2) }
    }
    public override fun info(marker: Marker?, format: String?, vararg arguments: Any?) {
        format.log(Level.Info) { java.lang.String.format(it, arguments) }
    }

    // WARN
    public override fun isWarnEnabled(): Boolean = delegate.isLoggable(Level.Warn)
    public override fun isWarnEnabled(marker: Marker?): Boolean = isWarnEnabled()

    public override fun warn(message: String) { delegate.w(message, t = null) }
    public override fun warn(message: String, cause: Throwable) { delegate.w(message, cause) }
    public override fun warn(marker: Marker?, message: String?) { delegate.w(message, t = null) }
    public override fun warn(marker: Marker?, message: String?, cause: Throwable?) { delegate.w(message, cause) }

    public override fun warn(format: String?, arg: Any?) {
        format.log(Level.Warn) { java.lang.String.format(it, arg) }
    }
    public override fun warn(format: String?, arg1: Any?, arg2: Any?) {
        format.log(Level.Warn) { java.lang.String.format(it, arg1, arg2) }
    }
    public override fun warn(format: String?, vararg arguments: Any?) {
        format.log(Level.Warn) { java.lang.String.format(it, arguments) }
    }

    public override fun warn(marker: Marker?, format: String?, arg: Any?) {
        format.log(Level.Warn) { java.lang.String.format(it, arg) }
    }
    public override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        format.log(Level.Warn) { java.lang.String.format(it, arg1, arg2) }
    }
    public override fun warn(marker: Marker?, format: String?, vararg arguments: Any?) {
        format.log(Level.Warn) { java.lang.String.format(it, arguments) }
    }

    // ERROR
    public override fun isErrorEnabled(): Boolean = delegate.isLoggable(Level.Error)
    public override fun isErrorEnabled(marker: Marker?): Boolean = isErrorEnabled()

    public override fun error(message: String) { delegate.e(message, t = null) }
    public override fun error(message: String, cause: Throwable) { delegate.e(message, cause) }
    public override fun error(marker: Marker?, message: String?) { delegate.e(message, t = null) }
    public override fun error(marker: Marker?, message: String?, cause: Throwable?) { delegate.e(message, cause) }

    public override fun error(format: String?, arg: Any?) {
        format.log(Level.Error) { java.lang.String.format(it, arg) }
    }
    public override fun error(format: String?, arg1: Any?, arg2: Any?) {
        format.log(Level.Error) { java.lang.String.format(it, arg1, arg2) }
    }
    public override fun error(format: String?, vararg arguments: Any?) {
        format.log(Level.Error) { java.lang.String.format(it, arguments) }
    }

    public override fun error(marker: Marker?, format: String?, arg: Any?) {
        format.log(Level.Error) { java.lang.String.format(it, arg) }
    }
    public override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        format.log(Level.Error) { java.lang.String.format(it, arg1, arg2) }
    }
    public override fun error(marker: Marker?, format: String?, vararg arguments: Any?) {
        format.log(Level.Error) { java.lang.String.format(it, arguments) }
    }

    /** @suppress */
    public override fun equals(other: Any?): Boolean {
        if (other !is KmpLogSLF4JLogger) return false
        return other.delegate == this.delegate
    }
    /** @suppress */
    public override fun hashCode(): Int {
        var result = 17
        result = result * 31 + this::class.hashCode()
        result = result * 31 + delegate.hashCode()
        return result
    }
    /** @suppress */
    public override fun toString(): String {
        return delegate.toString()
            .replaceBefore('[', "KmpLogSLF4JLogger")
            .replaceAfterLast('@', hashCode().toString())
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun String?.log(level: Level, block: (f: String) -> String) {
        contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
        // Log.Root ignores null/empty Strings and will simply return 0
        if (isNullOrEmpty()) return
        delegate.log(level) { block(this) }
    }
}
