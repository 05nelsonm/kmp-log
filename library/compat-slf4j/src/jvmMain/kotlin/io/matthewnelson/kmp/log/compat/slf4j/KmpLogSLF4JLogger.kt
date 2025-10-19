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
import io.matthewnelson.kmp.log.d
import io.matthewnelson.kmp.log.e
import io.matthewnelson.kmp.log.i
import io.matthewnelson.kmp.log.log
import io.matthewnelson.kmp.log.v
import io.matthewnelson.kmp.log.w
import org.slf4j.Marker

/**
 * Convert [Log.Logger] to [org.slf4j.Logger].
 *
 * **NOTE:** All [Marker] are ignored.
 *
 * @see [of]
 * @see [asSLF4JLogger]
 * */
public abstract class KmpLogSLF4JLogger private constructor(
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

            // The reason for all the fuckery is due to Ktor's Logger interface
            // expect/actual not honoring org.slf4j.Logger's nullability and function
            // argument names for its Jvm source set. So, these functions are defined
            // here in order to maintain compatibility and also utilize KmpLogSLF4JLogger
            // as a typealias for KmpLogKtorLogger in :compat-ktor.
            return object : KmpLogSLF4JLogger(logger) {

                private val _name = delegate.domain?.let { "[$it]${delegate.tag}" } ?: delegate.tag
                public override fun getName(): String = _name

                // TRACE
                override fun isTraceEnabled(): Boolean = delegate.isLoggable(Level.Verbose)
                override fun isTraceEnabled(marker: Marker?): Boolean = isTraceEnabled()

                override fun trace(msg: String?) { delegate.v(t = null, msg = msg) }
                override fun trace(msg: String?, t: Throwable?) { delegate.v(t = t, msg = msg) }
                override fun trace(marker: Marker?, msg: String?) { delegate.v(t = null, msg = msg) }
                override fun trace(marker: Marker?, msg: String?, t: Throwable?) { delegate.v(t = t, msg = msg) }

                override fun trace(format: String?, arg: Any?) { delegate.v(t = null, format, arg) }
                override fun trace(format: String?, arg1: Any?, arg2: Any?) { delegate.v(t = null, format, arg1, arg2) }
                override fun trace(format: String?, vararg arguments: Any?) { delegate.log(Level.Verbose, arguments, t = null, format) }

                override fun trace(marker: Marker?, format: String?, arg: Any?) { delegate.v(t = null, format, arg) }
                override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) { delegate.v(t = null, format, arg1, arg2) }
                override fun trace(marker: Marker?, format: String?, vararg argArray: Any?) { delegate.log(Level.Verbose, argArray, t = null, format) }

                // DEBUG
                override fun isDebugEnabled(): Boolean = delegate.isLoggable(Level.Debug)
                override fun isDebugEnabled(marker: Marker?): Boolean = isDebugEnabled()

                override fun debug(msg: String?) { delegate.d(t = null, msg = msg) }
                override fun debug(msg: String?, t: Throwable?) { delegate.d(t = t, msg = msg) }
                override fun debug(marker: Marker?, msg: String?) { delegate.d(t = null, msg = msg) }
                override fun debug(marker: Marker?, msg: String?, t: Throwable?) { delegate.d(t = t, msg = msg) }

                override fun debug(format: String?, arg: Any?) { delegate.d(t = null, format, arg) }
                override fun debug(format: String?, arg1: Any?, arg2: Any?) { delegate.d(t = null, format, arg1, arg2) }
                override fun debug(format: String?, vararg arguments: Any?) { delegate.log(Level.Debug, arguments, t = null, format) }

                override fun debug(marker: Marker?, format: String?, arg: Any?) { delegate.d(t = null, format, arg) }
                override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) { delegate.d(t = null, format, arg1, arg2) }
                override fun debug(marker: Marker?, format: String?, vararg arguments: Any?) { delegate.log(Level.Debug, arguments, t = null, format) }

                // INFO
                override fun isInfoEnabled(): Boolean = delegate.isLoggable(Level.Info)
                override fun isInfoEnabled(marker: Marker?): Boolean = isInfoEnabled()

                override fun info(msg: String?) { delegate.i(t = null, msg = msg) }
                override fun info(msg: String?, t: Throwable?) { delegate.i(t = t, msg = msg) }
                override fun info(marker: Marker?, msg: String?) { delegate.i(t = null, msg = msg) }
                override fun info(marker: Marker?, msg: String?, t: Throwable?) { delegate.i(t = t, msg = msg) }

                override fun info(format: String?, arg: Any?) { delegate.i(t = null, format, arg) }
                override fun info(format: String?, arg1: Any?, arg2: Any?) { delegate.i(t = null, format, arg1, arg2) }
                override fun info(format: String?, vararg arguments: Any?) { delegate.log(Level.Info, arguments, t = null, format) }

                override fun info(marker: Marker?, format: String?, arg: Any?) { delegate.i(t = null, format, arg) }
                override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) { delegate.i(t = null, format, arg1, arg2) }
                override fun info(marker: Marker?, format: String?, vararg arguments: Any?) { delegate.log(Level.Info, arguments, t = null, format) }

                // WARN
                override fun isWarnEnabled(): Boolean = delegate.isLoggable(Level.Warn)
                override fun isWarnEnabled(marker: Marker?): Boolean = isWarnEnabled()

                override fun warn(msg: String?) { delegate.w(t = null, msg = msg) }
                override fun warn(msg: String?, t: Throwable?) { delegate.w(t = t, msg = msg) }
                override fun warn(marker: Marker?, msg: String?) { delegate.w(t = null, msg = msg) }
                override fun warn(marker: Marker?, msg: String?, t: Throwable?) { delegate.w(t = t, msg = msg) }

                override fun warn(format: String?, arg: Any?) { delegate.w(t = null, format, arg) }
                override fun warn(format: String?, arg1: Any?, arg2: Any?) { delegate.w(t = null, format, arg1, arg2) }
                override fun warn(format: String?, vararg arguments: Any?) { delegate.log(Level.Warn, arguments, t = null, format) }

                override fun warn(marker: Marker?, format: String?, arg: Any?) { delegate.w(t = null, format, arg) }
                override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) { delegate.w(t = null, format, arg1, arg2) }
                override fun warn(marker: Marker?, format: String?, vararg arguments: Any?) { delegate.log(Level.Warn, arguments, t = null, format) }

                // ERROR
                override fun isErrorEnabled(): Boolean = delegate.isLoggable(Level.Error)
                override fun isErrorEnabled(marker: Marker?): Boolean = isErrorEnabled()

                override fun error(msg: String?) { delegate.e(t = null, msg = msg) }
                override fun error(msg: String?, t: Throwable?) { delegate.e(t = t, msg = msg) }
                override fun error(marker: Marker?, msg: String?) { delegate.e(t = null, msg = msg) }
                override fun error(marker: Marker?, msg: String?, t: Throwable?) { delegate.e(t = t, msg = msg) }

                override fun error(format: String?, arg: Any?) { delegate.e(t = null, format, arg) }
                override fun error(format: String?, arg1: Any?, arg2: Any?) { delegate.e(t = null, format, arg1, arg2) }
                override fun error(format: String?, vararg arguments: Any?) { delegate.log(Level.Error, arguments, t = null, format) }

                override fun error(marker: Marker?, format: String?, arg: Any?) { delegate.e(t = null, format, arg) }
                override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) { delegate.e(t = null, format, arg1, arg2) }
                override fun error(marker: Marker?, format: String?, vararg arguments: Any?) { delegate.log(Level.Error, arguments, t = null, format) }
            }
        }

        /**
         * So [KmpLogSLF4JLogger] can be used in `:compat-ktor` as a `typealias` for `KmpLogKtorLogger`.
         * @suppress
         * */
        @JvmStatic
        public inline fun Log.Logger.asKtorLogger(): KmpLogSLF4JLogger = of(this)
    }

    /** @suppress */
    public final override fun equals(other: Any?): Boolean {
        if (other !is KmpLogSLF4JLogger) return false
        return other.delegate == this.delegate
    }
    /** @suppress */
    public final override fun hashCode(): Int {
        var result = 17
        result = result * 31 + this::class.hashCode()
        result = result * 31 + delegate.hashCode()
        return result
    }
    /** @suppress */
    public final override fun toString(): String {
        return delegate.toString()
            .replaceBefore('[', "KmpLogSLF4JLogger")
            .replaceAfterLast('@', hashCode().toString())
    }
}
