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
@file:Suppress("LocalVariableName", "NOTHING_TO_INLINE", "RedundantCompanionReference", "RemoveRedundantQualifierName")

package io.matthewnelson.kmp.log

import io.matthewnelson.kmp.log.internal.ABORT_HANDLER_UID
import io.matthewnelson.kmp.log.internal.aborterAcceptsMessages
import io.matthewnelson.kmp.log.internal.commonCheckDomain
import io.matthewnelson.kmp.log.internal.commonCheckTag
import io.matthewnelson.kmp.log.internal.doAbort
import io.matthewnelson.kmp.log.internal.newLock
import io.matthewnelson.kmp.log.internal.withLock
import kotlin.concurrent.Volatile
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * An abstraction for dynamic logging.
 *
 * Various [Log] implementations can be installed into [Root] whereby [Logger]
 * logs get directed. By default, the only [Log] instance available at [Root] is
 * [AbortHandler]. If no other [Log] implementations are installed, then no logging
 * occurs (this is by design).
 *
 * Application developers are able to tailor logging to their needs, such as a debug
 * build [Log] implementation and a release [Log] implementation for crash reporting.
 *
 * Library authors also have the means to add granular logging to their codebase by
 * use of [Logger], while not sacrificing performance, leaving their end-users in
 * complete control over what logs are generated (if any). See [Logger.domain].
 *
 * e.g. (Using `SysLog` from `kmp-log:sys`)
 *
 *     val logger = Log.Logger.of(tag = "Example")
 *     logger.i { "This will not be logged" }
 *     Log.Root.install(SysLog.Debug)
 *     logger.i { "This WILL be logged" }
 *     Log.Root.uninstall(SysLog.UID)
 *
 * @see [Logger]
 * @see [Root]
 * */
public abstract class Log {

    public enum class Level {

        /**
         * See [Logger.v]
         * */
        Verbose,

        /**
         * See [Logger.d]
         * */
        Debug,

        /**
         * See [Logger.i]
         * */
        Info,

        /**
         * See [Logger.w]
         * */
        Warn,

        /**
         * See [Logger.e]
         * */
        Error,

        /**
         * See [Logger.wtf]
         * See [AbortHandler]
         *
         * **NOTE:** [Log] implementations should not abort or exit the program
         * when processing a log at this level; that is left to [AbortHandler]
         * if it is installed. [Log] implementations should commit the data to
         * their logs immediately for this [Level].
         * */
        Fatal,
    }

    /**
     * Logs things to installed [Log] implementation(s) at [Root].
     *
     * **NOTE:** Logs containing no data are ignored by all [Log] instances.
     *
     * e.g.
     *
     *     myLogger.wtf(t = null, msg = null)       // Ignored
     *     myLogger.wtf(t = null, msg = "")         // Ignored
     *     myLogger.wtf(msg = "")                   // Ignored
     *     myLogger.wtf { null }                    // Ignored
     *     myLogger.wtf { "" }                      // Ignored
     *     myLogger.wtf(t = null) { null }          // Ignored
     *     myLogger.wtf(t = null) { "" }            // Ignored
     *     myLogger.wtf {
     *         "This will be logged by all installed Log instances"
     *         " and then abort (if Log.AbortHandler is installed)."
     *     }
     *
     * @see [of]
     * */
    public class Logger private constructor(

        /**
         * A well-defined domain (e.g. `kmp-log:log`), or `null`. [Log] implementations
         * may use this for filtering purposes.
         *
         * The intended purpose of the domain API is for libraries. This helps prevent
         * tag clashes with end-user [Logger] instances, quicker identification of where
         * a log originated from, and provides the end-user with the ability to control
         * what (if any) logging occurs for a specific domain.
         *
         * @see [checkDomain]
         * */
        @JvmField
        public val domain: String?,

        /**
         * A well-defined tag, such as a class name.
         *
         * @see [checkTag]
         * */
        @JvmField
        public val tag: String,
    ) {

        public companion object {

            /**
             * Create a new [Logger] instance. If a [Logger] instance already exists
             * for the provided [tag] and `null` domain, then that is returned instead.
             *
             * @param [tag] The tag to use when logging.
             *
             * @return A [Logger] instance.
             *
             * @see [checkTag]
             *
             * @throws [IllegalArgumentException] If [checkTag] fails.
             * @throws [NullPointerException] If tag is `null`.
             * */
            @JvmStatic
            public inline fun of(tag: String?): Logger = of(tag, domain = null)

            /**
             * Create a new [Logger] instance. If a [Logger] instance already exists
             * for the provided [domain] and [tag], then that is returned instead.
             *
             * @param [domain] The domain to use when logging or `null`.
             * @param [tag] The tag to use when logging.
             *
             * @return A [Logger] instance.
             *
             * @see [checkDomain]
             * @see [checkTag]
             *
             * @throws [IllegalArgumentException] If [checkDomain] or [checkTag] fail.
             * @throws [NullPointerException] If tag is `null`.
             * */
            @JvmStatic
            public fun of(tag: String?, domain: String?): Logger {
                val _tag = checkTag(tag)
                val _domain = checkDomain(domain)

                var iNext = 0
                run {
                    val loggers = _LOGGERS
                    while (iNext < loggers.size) {
                        val logger = loggers[iNext] ?: break
                        if (logger.domain == _domain && logger.tag == _tag) return logger
                        iNext++
                    }
                }

                LOCK_LOGGERS.withLock {
                    // _LOGGERS only ever grows, so just need to check for
                    // any that may have been added while waiting on the lock.
                    val loggers = _LOGGERS
                    while (iNext < loggers.size) {
                        val logger = loggers[iNext] ?: break
                        if (logger.domain == _domain && logger.tag == _tag) return logger
                        iNext++
                    }
                    val logger = Logger(_domain, _tag)
                    if (iNext == loggers.size) {
                        val grow = loggers.copyOf(loggers.size + (loggers.size / 2))
                        grow[iNext] = logger
                        _LOGGERS = grow
                    } else {
                        loggers[iNext] = logger
                    }
                    return logger
                }
            }

            /**
             * Checks a `String` for validity of use as a [Logger.domain] value. This
             * provides a uniform standard for all [Log] implementations to rely on.
             *
             * A valid domain is either `null`, or complies with the following parameters:
             *  - Is greater than or equal to `3` characters in length.
             *  - Is less than or equal to `32` characters in length.
             *  - Contains only the following:
             *      - Characters `0` - `9`
             *      - Characters `a` - `z`
             *      - Separators `.`, `-`, `:`
             *  - Does not start or end with a separator character.
             *      - e.g. Invalid >> `.my.domain`
             *      - e.g. Invalid >> `my.domain.`
             *  - Contains at least `1` separator character.
             *      - e.g. Invalid >> `mydomain`
             *  - Separator characters do not precede or follow another separator character.
             *      - e.g. Invalid >> `my.:domain`
             *
             * @return The domain
             *
             * @throws [IllegalArgumentException] If domain is invalid.
             * */
            @JvmStatic
            @Throws(IllegalArgumentException::class)
            public fun checkDomain(domain: String?): String? = commonCheckDomain(domain)

            /**
             * Checks a `String` for validity of use as a [Logger.tag] value.
             *
             * A valid tag complies with the following parameters:
             *  - Is not `null`.
             *  - Is greater than or equal to `1` character in length (i.e. is not empty).
             *  - Is less than or equal to `128` characters in length.
             *  - Does not contain whitespace.
             *      - e.g. Invalid >> `My Tag`
             *      - e.g. Invalid >> `My\nTag`
             *
             * @return The non-`null` tag
             *
             * @throws [IllegalArgumentException] If tag is invalid.
             * @throws [NullPointerException] If tag is `null`.
             * */
            @JvmStatic
            @Throws(IllegalArgumentException::class, NullPointerException::class)
            public fun checkTag(tag: String?): String = commonCheckTag(tag)

            // Exposed for testing
            @JvmSynthetic
            internal fun size(): Int = _LOGGERS.count { it != null }

            private val LOCK_LOGGERS = newLock()
            @Volatile
            private var _LOGGERS: Array<Logger?> = arrayOfNulls(20)
        }

        /**
         * Send a [Level.Verbose] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun v(msg: String): Int = v(t = null, msg)

        /**
         * Send a [Level.Verbose] log message to all [Log] instances installed at [Root].
         *
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun v(t: Throwable): Int = v(t, msg = null)

        /**
         * Send a [Level.Verbose] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun v(t: Throwable?, msg: String?): Int = log(Level.Verbose, t, msg)

        /**
         * Send a [Level.Verbose] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        @JvmName("-v")
        public inline fun v(lazyMsg: () -> Any?): Int {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return v(t = null, lazyMsg)
        }

        /**
         * Send a [Level.Verbose] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         * @param [t] The error to log or `null`.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        @JvmName("-v")
        public inline fun v(t: Throwable?, lazyMsg: () -> Any?): Int {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return log(Level.Verbose, t, lazyMsg)
        }

        /**
         * Send a [Level.Debug] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun d(msg: String): Int = d(t = null, msg)

        /**
         * Send a [Level.Debug] log message to all [Log] instances installed at [Root].
         *
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun d(t: Throwable): Int = d(t, msg = null)

        /**
         * Send a [Level.Debug] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged.
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun d(t: Throwable?, msg: String?): Int = log(Level.Debug, t, msg)

        /**
         * Send a [Level.Debug] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        @JvmName("-d")
        public inline fun d(lazyMsg: () -> Any?): Int {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return d(t = null, lazyMsg)
        }

        /**
         * Send a [Level.Debug] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         * @param [t] The error to log or `null`.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        @JvmName("-d")
        public inline fun d(t: Throwable?, lazyMsg: () -> Any?): Int {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return log(Level.Debug, t, lazyMsg)
        }

        /**
         * Send a [Level.Info] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun i(msg: String): Int = i(t = null, msg)

        /**
         * Send a [Level.Info] log message to all [Log] instances installed at [Root].
         *
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun i(t: Throwable): Int = i(t, msg = null)

        /**
         * Send a [Level.Info] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun i(t: Throwable?, msg: String?): Int = log(Level.Info, t, msg)

        /**
         * Send a [Level.Info] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        @JvmName("-i")
        public inline fun i(lazyMsg: () -> Any?): Int {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return i(t = null, lazyMsg)
        }

        /**
         * Send a [Level.Info] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         * @param [t] The error to log or `null`.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        @JvmName("-i")
        public inline fun i(t: Throwable?, lazyMsg: () -> Any?): Int {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return log(Level.Info, t, lazyMsg)
        }

        /**
         * Send a [Level.Warn] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun w(msg: String): Int = w(t = null, msg)

        /**
         * Send a [Level.Warn] log message to all [Log] instances installed at [Root].
         *
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun w(t: Throwable): Int = w(t, msg = null)

        /**
         * Send a [Level.Warn] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun w(t: Throwable?, msg: String?): Int = log(Level.Warn, t, msg)

        /**
         * Send a [Level.Warn] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        @JvmName("-w")
        public inline fun w(lazyMsg: () -> Any?): Int {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return w(t = null, lazyMsg)
        }

        /**
         * Send a [Level.Warn] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         * @param [t] The error to log or `null`.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        @JvmName("-w")
        public inline fun w(t: Throwable?, lazyMsg: () -> Any?): Int {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return log(Level.Warn, t, lazyMsg)
        }

        /**
         * Send a [Level.Error] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun e(msg: String): Int = e(t = null, msg)

        /**
         * Send a [Level.Error] log message to all [Log] instances installed at [Root].
         *
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun e(t: Throwable): Int = e(t, msg = null)

        /**
         * Send a [Level.Error] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun e(t: Throwable?, msg: String?): Int = log(Level.Error, t, msg)

        /**
         * Send a [Level.Error] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        @JvmName("-e")
        public inline fun e(lazyMsg: () -> Any?): Int {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return e(t = null, lazyMsg)
        }

        /**
         * Send a [Level.Error] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         * @param [t] The error to log or `null`.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        @JvmName("-e")
        public inline fun e(t: Throwable?, lazyMsg: () -> Any?): Int {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return log(Level.Error, t, lazyMsg)
        }

        /**
         * Send a [Level.Fatal] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * **NOTE:** If [AbortHandler] is installed at [Root] (the default configuration),
         * this will cause the program to abort.
         *
         * @param [msg] The message to log.
         *
         * @return The number of [Log] instances that logged the data.
         *
         * @see [AbortHandler]
         * */
        public inline fun wtf(msg: String): Int = wtf(t = null, msg)

        /**
         * Send a [Level.Fatal] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** If [AbortHandler] is installed at [Root] (the default configuration),
         * this will cause the program to abort.
         *
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         *
         * @see [AbortHandler]
         * */
        public inline fun wtf(t: Throwable): Int = wtf(t, msg = null)

        /**
         * Send a [Level.Fatal] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * **NOTE:** If [AbortHandler] is installed at [Root] (the default configuration),
         * this will cause the program to abort.
         *
         * @param [msg] The message to log.
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         *
         * @see [AbortHandler]
         * */
        public inline fun wtf(t: Throwable?, msg: String?): Int = log(Level.Fatal, t, msg)

        /**
         * Send a [Level.Fatal] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * **NOTE:** If [AbortHandler] is installed at [Root] (the default configuration),
         * this will cause the program to abort.
         *
         * @param [lazyMsg] The message to log.
         *
         * @return The number of [Log] instances that logged the data.
         *
         * @see [AbortHandler]
         * */
        @JvmName("-wtf")
        public inline fun wtf(lazyMsg: () -> Any?): Int {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return wtf(t = null, lazyMsg)
        }

        /**
         * Send a [Level.Fatal] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * **NOTE:** If [AbortHandler] is installed at [Root] (the default configuration),
         * this will cause the program to abort.
         *
         * @param [lazyMsg] The message to log.
         * @param [t] The error to log or `null`.
         *
         * @return The number of [Log] instances that logged the data.
         *
         * @see [AbortHandler]
         * */
        @JvmName("-wtf")
        public inline fun wtf(t: Throwable?, lazyMsg: () -> Any?): Int {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return log(Level.Fatal, t, lazyMsg)
        }

        /**
         * Send a log message for specified [Level] to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [level] The [Level] of the log.
         * @param [msg] The message to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun log(level: Level, msg: String): Int = log(level, t = null, msg)

        /**
         * Send a log message for specified [Level] to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [level] The [Level] of the log.
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public inline fun log(level: Level, t: Throwable): Int = log(level, t, msg = null)

        /**
         * Send a log message for specified [Level] to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        public fun log(level: Level, t: Throwable?, msg: String?): Int = Root.log(logger = this, level, t, msg)

        /**
         * Send a log message for specified [Level] to all [Log] instances installed at [Root].
         * If no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [level] The [Level] of the log.
         * @param [lazyMsg] The message to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        @JvmName("-log")
        public inline fun log(level: Level, lazyMsg: () -> Any?): Int {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return log(level, t = null, lazyMsg)
        }

        /**
         * Send a log message for specified [Level] to all [Log] instances installed at [Root].
         * If no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [level] The [Level] of the log.
         * @param [lazyMsg] The message to log.
         * @param [t] The error to log.
         *
         * @return The number of [Log] instances that logged the data.
         * */
        @JvmName("-log")
        public inline fun log(level: Level, t: Throwable?, lazyMsg: () -> Any?): Int {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            if (!isLoggable(level)) return 0
            val msg = lazyMsg()?.toString()
            return log(level, t, msg)
        }

        /**
         * Checks if any [Log] instances installed at [Root] will accept a log at the provided
         * [Level] from this [Logger] instance.
         *
         * **NOTE:** This does not need to be called when utilizing [Logger] logging functions
         * [v], [d], [i], [w], [e], [wtf], or [log]. [Root] will perform the same check prior
         * to sending anything to an installed [Log] instance. This is exposed for the `lazyMsg`
         * inline function API to mitigate unnecessary `String` creation, as well as other
         * compatibility extension modules.
         *
         * @param [level] The level to check for this [Logger].
         *
         * @return `true` if a log at the provided [Level] for this [Logger] would be accepted,
         *   `false` otherwise.
         *
         * @see [Log.isLoggable]
         * */
        public fun isLoggable(level: Level): Boolean = Root.isLoggable(logger = this, level)

        /** @suppress */
        public override fun equals(other: Any?): Boolean {
            if (other !is Logger) return false
            if (other.domain != this.domain) return false
            return other.tag == this.tag
        }
        /** @suppress */
        public override fun hashCode(): Int {
            var result = 17
            result = result * 31 + this::class.hashCode()
            result = result * 31 + domain.hashCode()
            result = result * 31 + tag.hashCode()
            return result
        }
        /** @suppress */
        public override fun toString(): String {
            var result = "Log.Logger["
            if (domain != null) result += "domain=$domain, "
            result += "tag=$tag]@${hashCode()}"
            return result
        }
    }

    /**
     * The root location for which all [Log] instances are installed. By default, only
     * [AbortHandler] is available; [Log] implementations must be installed for logging
     * to occur.
     * */
    public companion object Root {

        /**
         * Returns a list of all [Log] instances that are currently installed.
         * */
        @JvmStatic
        public fun installed(): List<Log> = LOGS._ARRAY.toList()

        /**
         * Returns the [Log] instance currently installed where [Log.uid] matches that
         * which is specified, or `null` if no [Log] instances are found.
         * */
        @JvmStatic
        public operator fun get(uid: String): Log? = LOGS._ARRAY.firstOrNull { it.uid == uid }

        /**
         * Install a [Log] instance.
         *
         * @param [log] The [Log] instance to install.
         *
         * @return `true` if the [Log] instance was installed, or `false` if a [Log]
         *   instance with the same [Log.uid] is already installed.
         *
         * @see [installOrThrow]
         * */
        @JvmStatic
        public fun install(log: Log): Boolean {
            LOGS.withLockAndReentryGuard {
                val logs = _ARRAY
                if (logs.firstOrNull { it.uid == log.uid } != null) return false
                if (log.uid == AbortHandler.uid) {
                    require(log == AbortHandler) { "$log is not $AbortHandler" }
                    // Always install AbortHandler as the last instance
                    _ARRAY = arrayOf(*logs, log)
                    return true
                }
                log.doOnInstall()
                _ARRAY = arrayOf(log, *logs)
                return true
            }
        }

        /**
         * Install a [Log] instance.
         *
         * @param [log] The [Log] instance to install.
         *
         * @see [install]
         *
         * @throws [IllegalStateException] If a [Log] instance with the same [Log.uid]
         *   is already installed.
         * */
        @JvmStatic
        @Throws(IllegalStateException::class)
        public inline fun installOrThrow(log: Log) {
            check(install(log)) { "$log is already installed." }
        }

        /**
         * Uninstall all currently installed [Log] instances.
         *
         * @param [evenAbortHandler] If `true`, even the [AbortHandler] will be
         *   uninstalled. If `false`, [AbortHandler] will not be uninstalled if and
         *   only if it is currently installed (i.e. it will not be re-installed).
         * */
        @JvmStatic
        public fun uninstallAll(evenAbortHandler: Boolean) {
            LOGS.withLockAndReentryGuard {
                val logs = _ARRAY
                if (logs.isEmpty()) return
                _ARRAY = when {
                    evenAbortHandler -> emptyArray()
                    logs.contains(AbortHandler) -> if (logs.size == 1) return else arrayOf(AbortHandler)
                    else -> emptyArray()
                }
                var threw: Throwable? = null
                logs.forEach { log ->
                    try {
                        // Ideally this should never happen, but just in case it
                        // does, we do not want one bad Log implementation to
                        // affect other implementations.
                        log.doOnUninstall()
                    } catch (t: Throwable) {
                        if (threw?.addSuppressed(t) == null) threw = t
                    }
                }
                threw?.let { throw it }
            }
        }

        /**
         * Uninstall the [Log] instance, or a [Log] instance containing the same [Log.uid]
         * as the one provided.
         *
         * @param [log] The [Log] instance to uninstall.
         *
         * @return `true` if a [Log] instance, either the one provided or one containing
         *   the same [Log.uid], was uninstalled. `false` otherwise.
         *
         * @see [uninstallOrThrow]
         * */
        @JvmStatic
        public inline fun uninstall(log: Log): Boolean = uninstall(log.uid)

        /**
         * Uninstall a [Log] instance.
         *
         * @param [uid] The [Log.uid] of the instance to uninstall.
         *
         * @return `true` if a [Log] instance with a [Log.uid] matching the one
         *   provided was uninstalled. `false` otherwise.
         *
         * @see [uninstallOrThrow]
         * */
        @JvmStatic
        public fun uninstall(uid: String): Boolean {
            LOGS.withLockAndReentryGuard {
                val logs = _ARRAY
                val index = logs.indexOfFirst { it.uid == uid }
                if (index == -1) return false
                _ARRAY = if (logs.size == 1) {
                    emptyArray()
                } else {
                    val list = ArrayList<Log>(logs.size - 1)
                    for (i in logs.indices) {
                        if (i == index) continue
                        list.add(logs[i])
                    }
                    list.toTypedArray()
                }
                logs[index].doOnUninstall()
                return true
            }
        }

        /**
         * Uninstall the [Log] instance, or a [Log] instance containing the same [Log.uid]
         * as the one provided.
         *
         * @param [log] The [Log] instance to uninstall.
         *
         * @see [uninstall]
         *
         * @throws [IllegalStateException] If a [Log] instance, either the one provided or
         *   one containing the same [Log.uid], was **not** uninstalled.
         * */
        @JvmStatic
        @Throws(IllegalStateException::class)
        public inline fun uninstallOrThrow(log: Log) {
            uninstallOrThrow(log.uid)
        }

        /**
         * Uninstall a [Log] instance.
         *
         * @param [uid] The [Log.uid] of the instance to uninstall.
         *
         * @return `true` if a [Log] instance with a [Log.uid] matching the one
         *   provided was uninstalled. `false` otherwise.
         *
         * @see [uninstall]
         *
         * @throws [IllegalStateException] If a [Log] instance with a [Log.uid] matching
         *   the provided one was **not** uninstalled.
         * */
        @JvmStatic
        @Throws(IllegalStateException::class)
        public inline fun uninstallOrThrow(uid: String) {
            check(uninstall(uid)) { "A Log instance with uid[$uid] is not currently installed." }
        }

        private const val ROOT_DOMAIN: String = "kmp-log:log"
        private const val ROOT_TAG: String = "Log.Root"

        private val LOGS by lazy { Logs() }

        private fun isLoggable(logger: Logger, level: Level): Boolean {
            LOGS._ARRAY.forEach { log ->
                if (log.isLoggable(logger, level)) return true
            }
            return false
        }

        private fun log(logger: Logger, level: Level, t: Throwable?, msg: String?): Int {
            val m = if (msg.isNullOrEmpty()) null else msg
            if (m == null && t == null) return 0
            var loggedBy = 0
            LOGS._ARRAY.forEach { log ->
                if (!log.isLoggable(logger, level)) return@forEach
                val tOrFatal = if (loggedBy == 0 && level == AbortHandler.max && log == AbortHandler) {
                    // AbortHandler will always be installed as the last Log instance.
                    // If no Log instances have logged the Level.Fatal error yet, passing
                    // FatalException will cause it to print the stack trace before aborting.
                    FatalException(null, t)
                } else {
                    t
                }
                if (!log.log(level, logger.domain, logger.tag, m, tOrFatal)) return@forEach
                loggedBy++
            }
            return loggedBy
        }

        private inline fun Log.isLoggable(logger: Logger, level: Level): Boolean {
            if (level !in min..max) return false
            return isLoggable(level, logger.domain, logger.tag)
        }

        // OK to call on AbortHandler b/c it does nothing
        private inline fun Log.doOnInstall() {
            onInstall()
            if (Level.Debug !in min..max) return
            if (!isLoggable(Level.Debug, ROOT_DOMAIN, ROOT_TAG)) return
            log(Level.Debug, ROOT_DOMAIN, ROOT_TAG, toString() + ".onInstall()", null)
        }

        // OK to call on AbortHandler b/c it does nothing
        private inline fun Log.doOnUninstall() {
            try {
                if (Level.Debug !in min..max) return
                if (!isLoggable(Level.Debug, ROOT_DOMAIN, ROOT_TAG)) return
                log(Level.Debug, ROOT_DOMAIN, ROOT_TAG, toString() + ".onUninstall()", null)
            } finally {
                onUninstall()
            }
        }

        /** @suppress */
        public override fun toString(): String = ROOT_TAG

        // Must wrap variables in a class and initialize lazily b/c Jvm
        // throws a fit due to AbortController.INSTANCE being null.
        @Suppress("PropertyName")
        private class Logs {
            val LOCK = newLock()

            // All modifications are guarded by withLockAndReentryGuard
            @Volatile
            var _ARRAY: Array<Log> = arrayOf(AbortHandler)

            // For guarding against Logs calling install/uninstall functions
            // from their onInstall/onUninstall implementations.
            @Volatile
            var _REENTRY_GUARD: Boolean = false
        }

        private inline fun <R> Logs.withLockAndReentryGuard(block: Logs.() -> R): R {
            contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
            LOCK.withLock {
                check(!_REENTRY_GUARD) {
                    "Log.Root.{install/uninstall} functions cannot be called from Log.{onInstall/onUninstall}"
                }

                _REENTRY_GUARD = true
                try {
                    return block()
                } finally {
                    _REENTRY_GUARD = false
                }
            }
        }
    }

    /**
     * A [Log] instance that, when installed (the default configuration), will handle
     * finalization of [Level.Fatal] logs by aborting the program. This instance will
     * always be the last [Root.installed] instance, giving a chance for all other
     * installed [Log] instances to capture the log. If no other [Log] instances logged
     * the [Level.Fatal] log, [printStackTrace] will be used to output the error before
     * aborting.
     *
     * Abort handling is performed in the following manner:
     *  - Android: [android.util.Log.wtf](https://developer.android.com/reference/android/util/Log#wtf(java.lang.String,%20java.lang.String))
     *  - Jvm/AndroidUnitTest: [Runtime.halt](https://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html#halt-int-)
     *  - Js/WasmJs:
     *      - Browser: Throw exception
     *      - Node.js: [process.abort](https://nodejs.org/api/process.html#processabort)
     *  - WasmWasi: [proc_exit](https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md#-proc_exitrval-exitcode)
     *  - Native:
     *      - Android:
     *          - API 30+: [__android_log_call_aborter](https://cs.android.com/android/platform/superproject/+/android-latest-release:system/logging/liblog/include/android/log.h;l=336)
     *          - API 29-: [abort](https://man7.org/linux/man-pages/man3/abort.3p.html)
     *      - Darwin/Linux/MinGW: [abort](https://man7.org/linux/man-pages/man3/abort.3p.html)
     * */
    public object AbortHandler: Log(uid = ABORT_HANDLER_UID, min = Level.Fatal) {

        /**
         * The [AbortHandler.uid] (i.e. `io.matthewnelson.kmp.log.Log.AbortHandler`)
         *
         * Can be used with [Log.Root.uninstall]
         * */
        public const val UID: String = ABORT_HANDLER_UID

        /**
         * Checks if [AbortHandler] is installed at [Root].
         * */
        @JvmStatic
        @get:JvmName("isInstalled")
        public val isInstalled: Boolean get() = Root[UID] != null

        override fun log(
            level: Level,
            domain: String?,
            tag: String,
            msg: String?,
            t: Throwable?,
        ): Boolean {
            val abortErr = if (t is FatalException) {
                // No Log instances logged the error. Repackage and print.
                var message = domain?.let { "[$it]$tag" } ?: tag
                if (msg != null) message += ": $msg"
                val e = FatalException(message, t.cause)
                if (aborterAcceptsMessages()) {
                    e
                } else {
                    e.printStackTrace()
                    null
                }
            } else {
                null
            }
            return doAbort(t = abortErr)
        }
    }

    /**
     * The minimum log level for this [Log] instance. Logs with a level less
     * than this will be ignored.
     * */
    @JvmField
    public val min: Level

    /**
     * The maximum log level for this [Log] instance. Logs with a level greater
     * than this will be ignored.
     *
     * **NOTE:** This value is guaranteed to be greater than or equal to [min].
     * */
    @JvmField
    public val max: Level

    /**
     * A unique identifier for this [Log] instance, such as a package name or
     * a hash of a file path. [Root.install] uses this value to inhibit multiple
     * instances from being installed.
     *
     * Will be non-empty and contain no whitespace.
     * */
    @JvmField
    public val uid: String

    /**
     * Instantiate a new [Log] instance.
     *
     * **NOTE:** The instance must still be installed via [Root.install].
     *
     * @param [uid] See [Log.uid]
     * @param [min] See [Log.min]
     *
     * @throws [IllegalArgumentException] If [uid] is an empty string or contains whitespace.
     * */
    protected constructor(uid: String, min: Level): this(uid, min, max = Level.Fatal)

    /**
     * Instantiate a new [Log] instance.
     *
     * **NOTE:** The instance must still be installed via [Root.install].
     *
     * @param [uid] See [Log.uid]
     * @param [min] See [Log.min]
     * @param [max] See [Log.max]
     *
     * @throws [IllegalArgumentException] If [uid] is an empty string or contains whitespace.
     * */
    protected constructor(uid: String, min: Level, max: Level) {
        require(uid.isNotEmpty()) { "uid cannot be empty" }
        require(uid.indexOfFirst { it.isWhitespace() } == -1) { "uid cannot contain whitespace" }
        this.min = min
        this.max = if (max >= min) max else min
        this.uid = uid
    }

    /**
     * Log something.
     *
     * Guarantees:
     *  - [level] will be between [min] and [max] (inclusive).
     *  - [domain] will be `null`, or in compliance with parameters specified by [Logger.checkDomain].
     *  - [tag] will be in compliance with parameters specified by [Logger.checkTag].
     *  - [msg] and [t] will never both be `null`.
     *  - [msg] will be `null` or a non-empty value, never empty.
     *  - [isLoggable] will have returned `true` immediately prior to this function being called by [Root].
     *
     * @return `true` if a log was generated, `false` otherwise.
     * */
    protected abstract fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean

    /**
     * Helper for implementations to filter by a logger's domain and/or tag.
     *
     * Guarantees:
     *  - [level] will be between [min] and [max] (inclusive).
     *  - [domain] will be `null`, or in compliance with parameters specified by [Logger.checkDomain].
     *  - [tag] will be in compliance with parameters specified by [Logger.checkTag].
     *
     * @return `true` if the log would be accepted, `false` otherwise. Default: `true`
     * */
    protected open fun isLoggable(level: Level, domain: String?, tag: String): Boolean = true

    /**
     * Helper for implementations to delay initialization of things to time of [Root.install]. This
     * is called just prior to making the [Log] available to logging functions, and is done so while
     * holding a lock. Implementations should be fast, non-blocking, and not throw exception.
     * */
    protected open fun onInstall() {}

    /**
     * Helper for implementations to clean up any resources at time of [Root.uninstall]. This is
     * called after the [Log] has been removed from the list of available [Log], and is done so while
     * holding a lock. Implementations should be fast, non-blocking, and not throw exception.
     * */
    protected open fun onUninstall() {}

    /** @suppress */
    public final override fun equals(other: Any?): Boolean {
        if (other !is Log) return false
        return other.hashCode() == this.hashCode()
    }
    /** @suppress */
    public final override fun hashCode(): Int {
        var result = 17
        result = result * 42 + this::class.hashCode()
        result = result * 42 + min.hashCode()
        result = result * 42 + max.hashCode()
        result = result * 42 + uid.hashCode()
        return result
    }
    /** @suppress */
    public final override fun toString(): String {
        val name = this::class.simpleName ?: "Log"
        return "$name[min=$min, max=$max, uid=$uid]"
    }

    // A way to signal to AbortHandler that no Log instances were installed to log
    // the Fatal error, and that it should print the stacktrace before aborting.
    private class FatalException(message: String?, cause: Throwable?): Throwable(message, cause)
}
