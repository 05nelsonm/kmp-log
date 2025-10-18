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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "UNUSED")

package io.matthewnelson.kmp.log.sys

import io.matthewnelson.kmp.log.Log

/**
 * Formats and then prints logs to the following system locations.
 *
 * - Android: [android.util.Log.println](https://developer.android.com/reference/android/util/Log#println(int,%20java.lang.String,%20java.lang.String))
 * - Jvm/AndroidUnitTest: [System.out](https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#out)/[System.err](https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#err)
 * - Js/WasmJs: [Console](https://developer.mozilla.org/en-US/docs/Web/API/console)
 * - WasmWasi: [fd_write](https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md#-fd_writefd-fd-iovs-ciovec_array---resultsize-errno) to `STDOUT_FILENO`/`STDERR_FILENO`
 * - Native:
 *     - Android: [__android_log_print](https://cs.android.com/android/platform/superproject/+/android-latest-release:system/logging/liblog/include/android/log.h;l=115)
 *     - Darwin/Linux/MinGW: [fprintf](https://www.man7.org/linux/man-pages/man3/fprintf.3p.html) to `stdout`/`stderr`
 *
 * **NOTE:** On Android & AndroidNative API 25 and below, [Logger.domain] is never used
 * and [Logger.tag] will be truncated (if necessary) to 23 characters.
 *
 * **NOTE:** On Android, AndroidNative, Js/WasmJs Node, and Js/WasmJs Browser, there are
 * log length limitations of `4_000`, `1_000`, `8_000`, and `2_000` characters, respectively.
 * In the event a log exceeds the maximum, [SysLog] will log in chunks of the maximum
 * allowable size, using the last available new line character (i.e. `\n`) for each chunk.
 * If no new line character is available, then splitting by last available whitespace occurs.
 * Lastly, if no whitespace is available, the maximum allowable length is used.
 *
 * Except for Android & AndroidNative (Logcat has its own format), the same format is applied
 * for all other platforms; it resembles Android Logcat almost identically. Do note that time
 * is displayed using local time, with the caveat of WasmWasi which is in `UTC` and indicates
 * so with the trailing `Z` character (e.g. `10-17 22:19:16.179Z`).
 *
 * e.g.
 *
 *     Log.install(SysLog.Default)
 *     Log.Logger.of(tag = "YourLogger").i("Hello World!")
 *     Log.Logger.of(domain = "your.domain", tag = "YourLogger2").w("Yo!")
 *
 *     // 10-17 22:19:16.179 D [kmp-log:log]Log.Root: SysLog[min=Verbose, max=Fatal, uid=io.matthewnelson.kmp.log.sys.SysLog].onInstall()
 *     // 10-17 22:19:16.179 I YourLogger: Hello World!
 *     // 10-17 22:19:16.181 W [your.domain]YourLogger2: Yo!
 * */
public expect open class SysLog private constructor(
    min: Level = Level.Debug,
): Log {

    /**
     * The default [SysLog] implementation with [SysLog.min] set to [Level.Debug].
     * */
    public companion object Default: SysLog {

        /**
         * The [SysLog.uid] (i.e. `io.matthewnelson.kmp.log.sys.SysLog`).
         *
         * Can be used with [Log.Root.uninstall].
         * */
        public /* const */ val UID: String /* = "io.matthewnelson.kmp.log.sys.SysLog" */

        /**
         * Checks if [SysLog] is installed at [Log.Root].
         * */
        public val isInstalled: Boolean

        /**
         * Instantiate a new [SysLog] instance with the specified minimum [Level]. If
         * [Level] specified is the same as [Default], then [Default] will be returned.
         * */
        public fun of(
            min: Level,
        ): SysLog
    }

    final override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean

    final override fun isLoggable(level: Level, domain: String?, tag: String): Boolean
}
