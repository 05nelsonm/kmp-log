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

import io.matthewnelson.kmp.log.internal.commonCheckDomain
import io.matthewnelson.kmp.log.internal.commonCheckTag
import io.matthewnelson.kmp.log.internal.newLock
import io.matthewnelson.kmp.log.internal.withLock
import kotlin.concurrent.Volatile
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * An abstraction for dynamic logging.
 *
 * Various [Log] implementations can be installed into [Root], whereby [Logger]
 * logs get directed. By default, no [Log] instances are available at [Root]; one
 * must be installed. If no [Log] implementations are installed, then no logging
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
 *     logger.d { "This will not be logged" }
 *     Log.Root.install(SysLog.Default)
 *     logger.d { "This WILL be logged" }
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
         *
         * **NOTE:** Logs generated at this level, depending on the platform, configuration,
         * and installed [Log] implementation(s), may cause the process to abort. [Log]
         * implementations should log these exceptions immediately, regardless.
         * */
        Fatal,
    }

    /**
     * Logs things to installed [Log] implementation(s) at [Root].
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

                var i = 0
                run {
                    val limit = LOGGERS.size
                    while (i < limit) {
                        val logger = LOGGERS[i]
                        if (logger.domain == _domain && logger.tag == _tag) return logger
                        i++
                    }
                }

                LOCK_LOGGERS.withLock {
                    // LOGGERS only ever grows, so just need to check for
                    // any that may have been added while waiting on the lock.
                    while (i < LOGGERS.size) {
                        val logger = LOGGERS[i]
                        if (logger.domain == _domain && logger.tag == _tag) return logger
                        i++
                    }

                    val logger = Logger(_domain, _tag)
                    LOGGERS.add(logger)
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
            internal fun size(): Int = LOGGERS.size

            private val LOCK_LOGGERS = newLock()
            private val LOGGERS = ArrayList<Logger>(20)
        }

        /**
         * Send a [Level.Verbose] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun v(msg: String): Boolean = v(msg, t = null)

        /**
         * Send a [Level.Verbose] log message to all [Log] instances installed at [Root].
         *
         * @param [t] The error to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun v(t: Throwable): Boolean = v(msg = null, t)

        /**
         * Send a [Level.Verbose] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         * @param [t] The error to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun v(msg: String?, t: Throwable?): Boolean = log(Level.Verbose, msg, t)

        /**
         * Send a [Level.Verbose] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun v(lazyMsg: () -> Any): Boolean {
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
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun v(t: Throwable?, lazyMsg: () -> Any): Boolean {
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
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun d(msg: String): Boolean = d(msg, t = null)

        /**
         * Send a [Level.Debug] log message to all [Log] instances installed at [Root].
         *
         * @param [t] The error to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun d(t: Throwable): Boolean = d(msg = null, t)

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
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun d(msg: String?, t: Throwable?): Boolean = log(Level.Debug, msg, t)

        /**
         * Send a [Level.Debug] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun d(lazyMsg: () -> Any): Boolean {
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
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun d(t: Throwable?, lazyMsg: () -> Any): Boolean {
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
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun i(msg: String): Boolean = i(msg, t = null)

        /**
         * Send a [Level.Info] log message to all [Log] instances installed at [Root].
         *
         * @param [t] The error to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun i(t: Throwable): Boolean = i(msg = null, t)

        /**
         * Send a [Level.Info] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         * @param [t] The error to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun i(msg: String?, t: Throwable?): Boolean = log(Level.Info, msg, t)

        /**
         * Send a [Level.Info] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun i(lazyMsg: () -> Any): Boolean {
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
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun i(t: Throwable?, lazyMsg: () -> Any): Boolean {
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
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun w(msg: String): Boolean = w(msg, t = null)

        /**
         * Send a [Level.Warn] log message to all [Log] instances installed at [Root].
         *
         * @param [t] The error to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun w(t: Throwable): Boolean = w(msg = null, t)

        /**
         * Send a [Level.Warn] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         * @param [t] The error to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun w(msg: String?, t: Throwable?): Boolean = log(Level.Warn, msg, t)

        /**
         * Send a [Level.Warn] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun w(lazyMsg: () -> Any): Boolean {
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
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun w(t: Throwable?, lazyMsg: () -> Any): Boolean {
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
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun e(msg: String): Boolean = e(msg, t = null)

        /**
         * Send a [Level.Error] log message to all [Log] instances installed at [Root].
         *
         * @param [t] The error to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun e(t: Throwable): Boolean = e(msg = null, t)

        /**
         * Send a [Level.Error] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         * @param [t] The error to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun e(msg: String?, t: Throwable?): Boolean = log(Level.Error, msg, t)

        /**
         * Send a [Level.Error] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun e(lazyMsg: () -> Any): Boolean {
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
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun e(t: Throwable?, lazyMsg: () -> Any): Boolean {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return log(Level.Error, t, lazyMsg)
        }

        /**
         * Send a [Level.Fatal] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun wtf(msg: String): Boolean = wtf(msg, t = null)

        /**
         * Send a [Level.Fatal] log message to all [Log] instances installed at [Root].
         *
         * @param [t] The error to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun wtf(t: Throwable): Boolean = wtf(msg = null, t)

        /**
         * Send a [Level.Fatal] log message to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         * @param [t] The error to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun wtf(msg: String?, t: Throwable?): Boolean = log(Level.Fatal, msg, t)

        /**
         * Send a [Level.Fatal] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun wtf(lazyMsg: () -> Any): Boolean {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            return wtf(t = null, lazyMsg)
        }

        /**
         * Send a [Level.Fatal] log message to all [Log] instances installed at [Root]. If
         * no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [lazyMsg] The message to log.
         * @param [t] The error to log or `null`.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun wtf(t: Throwable?, lazyMsg: () -> Any): Boolean {
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
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun log(level: Level, msg: String): Boolean = log(level, msg, t = null)

        /**
         * Send a log message for specified [Level] to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [level] The [Level] of the log.
         * @param [t] The error to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun log(level: Level, t: Throwable): Boolean = log(level, msg = null, t)

        /**
         * Send a log message for specified [Level] to all [Log] instances installed at [Root].
         *
         * **NOTE:** The `lazyMsg` inline version of this function should be preferred when
         * possible to mitigate unnecessary `String` creation.
         *
         * @param [msg] The message to log.
         * @param [t] The error to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public fun log(level: Level, msg: String?, t: Throwable?): Boolean = Root.log(logger = this, level, msg, t)

        /**
         * Send a log message for specified [Level] to all [Log] instances installed at [Root].
         * If no [Log] instances are installed, or none will accept it, then nothing is logged
         * and [lazyMsg] will not be invoked.
         *
         * @param [level] The [Level] of the log.
         * @param [lazyMsg] The message to log.
         *
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun log(level: Level, lazyMsg: () -> Any): Boolean {
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
         * @return `true` if it was logged by a [Log] instance, `false` otherwise.
         * */
        public inline fun log(level: Level, t: Throwable?, lazyMsg: () -> Any): Boolean {
            contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
            if (!isLoggable(level)) return false
            val msg = lazyMsg().toString()
            return log(level, msg, t)
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
     * The root location for which all [Log] instances are installed.
     * */
    public companion object Root {

        /**
         * Returns a list of all [Log] instances that are currently installed.
         * */
        @JvmStatic
        public fun installed(): List<Log> = _LOGS.toList()

        /**
         * Returns the [Log] instance currently installed where [Log.uid] matches that
         * which is specified, or `null` if no [Log] instances are found.
         * */
        @JvmStatic
        public operator fun get(uid: String): Log? = _LOGS.firstOrNull { it.uid == uid }

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
            LOCK_LOGS.withLock {
                val logs = _LOGS
                if (logs.firstOrNull { it.uid == log.uid } != null) return false
                log.doOnInstall()
                _LOGS = arrayOf(log, *logs)
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
         * */
        @JvmStatic
        public fun uninstallAll() {
            LOCK_LOGS.withLock {
                val logs = _LOGS
                _LOGS = emptyArray()
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
            LOCK_LOGS.withLock {
                val logs = _LOGS
                val index = logs.indexOfFirst { it.uid == uid }
                if (index == -1) return false
                _LOGS = if (logs.size == 1) {
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

        private val LOCK_LOGS = newLock()
        @Volatile
        private var _LOGS: Array<Log> = emptyArray()

        private fun isLoggable(logger: Logger, level: Level): Boolean {
            _LOGS.forEach { log ->
                if (log.isLoggable(logger, level)) return true
            }
            return false
        }

        private fun log(logger: Logger, level: Level, msg: String?, t: Throwable?): Boolean {
            val m = if (msg.isNullOrEmpty()) null else msg
            if (m == null && t == null) return false
            var wasLogged = false
            _LOGS.forEach { log ->
                if (!log.isLoggable(logger, level)) return@forEach
                if (!log.log(level, logger.domain, logger.tag, m, t)) return@forEach
                wasLogged = true
            }
            return wasLogged
        }

        private inline fun Log.isLoggable(logger: Logger, level: Level): Boolean {
            if (level !in min..max) return false
            return isLoggable(level, logger.domain, logger.tag)
        }

        private inline fun Log.doOnInstall() {
            onInstall()
            if (Level.Debug !in min..max) return
            if (!isLoggable(Level.Debug, ROOT_DOMAIN, ROOT_TAG)) return
            log(Level.Debug, ROOT_DOMAIN, ROOT_TAG, toString() + ".onInstall()", null)
        }

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
     *
     * **NOTE:** Implementations must **NOT** call any [Root] install/uninstall functions from here
     * as it will result in a deadlock.
     * */
    protected open fun onInstall() {}

    /**
     * Helper for implementations to clean up any resources at time of [Root.uninstall]. This is
     * called after the [Log] has been removed from the list of available [Log], and is done so while
     * holding a lock. Implementations should be fast, non-blocking, and not throw exception.
     *
     * **NOTE:** Implementations must **NOT** call any [Root] install/uninstall functions from here
     * as it will result in a deadlock.
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
}
