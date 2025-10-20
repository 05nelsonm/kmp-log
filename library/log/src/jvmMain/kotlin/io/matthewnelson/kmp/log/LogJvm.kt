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
@file:JvmName("LogJvm")
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.log

import io.matthewnelson.kmp.log.Log.Level
import io.matthewnelson.kmp.log.internal.withFormatter
import java.util.Formatter
import java.util.IllegalFormatException
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Send a [Level.Verbose] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arg] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arg] The argument to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.v(t: Throwable?, format: String?, arg: Any?): Int = log(Level.Verbose, t, format, arg)

/**
 * Send a [Level.Verbose] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arg1] and [arg2] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arg1] The first argument to apply to [format] via [Formatter.format].
 * @param [arg2] The second argument to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.v(t: Throwable?, format: String?, arg1: Any?, arg2: Any?): Int = log(Level.Verbose, t, format, arg1, arg2)

/**
 * Send a [Level.Verbose] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arguments] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arguments] The arguments to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.v(t: Throwable?, format: String?, vararg arguments: Any?): Int = log(Level.Verbose, arguments, t, format)

/**
 * Send a [Level.Debug] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arg] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arg] The argument to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.d(t: Throwable?, format: String?, arg: Any?): Int = log(Level.Debug, t, format, arg)

/**
 * Send a [Level.Debug] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arg1] and [arg2] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arg1] The first argument to apply to [format] via [Formatter.format].
 * @param [arg2] The second argument to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.d(t: Throwable?, format: String?, arg1: Any?, arg2: Any?): Int = log(Level.Debug, t, format, arg1, arg2)

/**
 * Send a [Level.Debug] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arguments] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arguments] The arguments to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.d(t: Throwable?, format: String?, vararg arguments: Any?): Int = log(Level.Debug, arguments, t, format)

/**
 * Send a [Level.Info] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arg] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arg] The argument to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.i(t: Throwable?, format: String?, arg: Any?): Int = log(Level.Info, t, format, arg)

/**
 * Send a [Level.Info] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arg1] and [arg2] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arg1] The first argument to apply to [format] via [Formatter.format].
 * @param [arg2] The second argument to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.i(t: Throwable?, format: String?, arg1: Any?, arg2: Any?): Int = log(Level.Info, t, format, arg1, arg2)

/**
 * Send a [Level.Info] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arguments] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arguments] The arguments to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.i(t: Throwable?, format: String?, vararg arguments: Any?): Int = log(Level.Info, arguments, t, format)

/**
 * Send a [Level.Warn] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arg] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arg] The argument to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.w(t: Throwable?, format: String?, arg: Any?): Int = log(Level.Warn, t, format, arg)

/**
 * Send a [Level.Warn] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arg1] and [arg2] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arg1] The first argument to apply to [format] via [Formatter.format].
 * @param [arg2] The second argument to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.w(t: Throwable?, format: String?, arg1: Any?, arg2: Any?): Int = log(Level.Warn, t, format, arg1, arg2)

/**
 * Send a [Level.Warn] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arguments] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arguments] The arguments to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.w(t: Throwable?, format: String?, vararg arguments: Any?): Int = log(Level.Warn, arguments, t, format)

/**
 * Send a [Level.Error] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arg] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arg] The argument to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.e(t: Throwable?, format: String?, arg: Any?): Int = log(Level.Error, t, format, arg)

/**
 * Send a [Level.Error] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arg1] and [arg2] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arg1] The first argument to apply to [format] via [Formatter.format].
 * @param [arg2] The second argument to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.e(t: Throwable?, format: String?, arg1: Any?, arg2: Any?): Int = log(Level.Error, t, format, arg1, arg2)

/**
 * Send a [Level.Error] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arguments] to [format].
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arguments] The arguments to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.e(t: Throwable?, format: String?, vararg arguments: Any?): Int = log(Level.Error, arguments, t, format)

/**
 * Send a [Level.Fatal] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arg] to [format].
 *
 * **NOTE:** If [Log.AbortHandler] is installed at [Log.Root] (the default configuration),
 * this will cause the program to abort.
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arg] The argument to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.wtf(t: Throwable?, format: String?, arg: Any?): Int = log(Level.Fatal, t, format, arg)

/**
 * Send a [Level.Fatal] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arg1] and [arg2] to [format].
 *
 * **NOTE:** If [Log.AbortHandler] is installed at [Log.Root] (the default configuration),
 * this will cause the program to abort.
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arg1] The first argument to apply to [format] via [Formatter.format].
 * @param [arg2] The second argument to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.wtf(t: Throwable?, format: String?, arg1: Any?, arg2: Any?): Int = log(Level.Fatal, t, format, arg1, arg2)

/**
 * Send a [Level.Fatal] log message to all [Log] instances installed at [Log.Root]. If
 * no [Log] instances are installed, or none will accept it, then nothing is logged and
 * [Formatter.format] will not be called to apply [arguments] to [format].
 *
 * **NOTE:** If [Log.AbortHandler] is installed at [Log.Root] (the default configuration),
 * this will cause the program to abort.
 *
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arguments] The arguments to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.wtf(t: Throwable?, format: String?, vararg arguments: Any?): Int = log(Level.Fatal, arguments, t, format)

/**
 * Send a log message to all [Log] instances installed at [Log.Root]. If no [Log] instances
 * are installed, or none will accept it, then nothing is logged and [Formatter.format] will
 * not be called to apply [arg] to [format].
 *
 * @param [level] The [Log.Level] to log at.
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arg] The argument to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public fun Log.Logger.log(level: Level, t: Throwable?, format: String?, arg: Any?): Int {
    return log(level, t, format) { unformatted ->
        // Formatter.format uses vararg, so calling lazily means
        // underlying Array is not created unnecessarily for the 1 arg.
        unformatted.withFormatter(_format = { s -> format(s, arg) })
    }
}

/**
 * Send a log message to all [Log] instances installed at [Log.Root]. If no [Log] instances
 * are installed, or none will accept it, then nothing is logged and [Formatter.format] will
 * not be called to apply [arg1] and [arg2] to [format].
 *
 * @param [level] The [Log.Level] to log at.
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arg1] The first argument to apply to [format] via [Formatter.format].
 * @param [arg2] The second argument to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public fun Log.Logger.log(level: Level, t: Throwable?, format: String?, arg1: Any?, arg2: Any?): Int {
    return log(level, t, format) { unformatted ->
        // Formatter.format uses vararg, so calling lazily means
        // underlying Array is not created unnecessarily for the 2 args.
        unformatted.withFormatter(_format = { s -> format(s, arg1, arg2) })
    }
}

/**
 * Send a log message to all [Log] instances installed at [Log.Root]. If no [Log] instances
 * are installed, or none will accept it, then nothing is logged and [Formatter.format] will
 * not be called to apply [arguments] to [format].
 *
 * @param [level] The [Log.Level] to log at.
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arguments] The arguments to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
public inline fun Log.Logger.log(level: Level, t: Throwable?, format: String?, vararg arguments: Any?): Int = log(level, arguments, t, format)

/**
 * Send a log message to all [Log] instances installed at [Log.Root]. If no [Log] instances
 * are installed, or none will accept it, then nothing is logged and [Formatter.format] will
 * not be called to apply [arguments] to [format].
 *
 * @param [level] The [Log.Level] to log at.
 * @param [t] The error to log.
 * @param [format] The unformatted message to log.
 * @param [arguments] The arguments to apply to [format] via [Formatter.format].
 *
 * @return The number of [Log] instances that logged the data.
 *
 * @throws [IllegalFormatException] If [Formatter.format] fails.
 * */
// Yes the function signature is not ideal, but it makes things cleaner when wanting to properly deal with vararg
public fun Log.Logger.log(level: Level, arguments: Array<out Any?>, t: Throwable?, format: String?): Int {
    return log(level, t, format) { unformatted ->
        unformatted.withFormatter(_format = { s -> format(s, *arguments) })
    }
}

private inline fun Log.Logger.log(level: Level, t: Throwable?, format: String?, block: (unformatted: String) -> String): Int {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    // Must check for null/empty String & null Throwable here b/c the Log.Logger.log inline
    // function would check Log.Logger.isLoggable before invoking its block parameter, which
    // in our case may be unnecessary if we have no String to format.
    val unformatted = if (format.isNullOrEmpty()) null else format
    if (unformatted == null && t == null) return 0
    return log(level, t) { if (unformatted != null) block(unformatted) else null }
}
