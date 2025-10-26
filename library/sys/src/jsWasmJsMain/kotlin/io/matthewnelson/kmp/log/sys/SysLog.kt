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

package io.matthewnelson.kmp.log.sys

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.internal.node.isNodeJs
import io.matthewnelson.kmp.log.sys.internal.SYS_LOG_UID
import io.matthewnelson.kmp.log.sys.internal.commonFormatLogOrNull
import io.matthewnelson.kmp.log.sys.internal.commonFormatDateTime
import io.matthewnelson.kmp.log.sys.internal.commonIsInstalled
import io.matthewnelson.kmp.log.sys.internal.commonLogChunk
import io.matthewnelson.kmp.log.sys.internal.commonOf
import io.matthewnelson.kmp.log.sys.internal.js.JsDate
import io.matthewnelson.kmp.log.sys.internal.js.jsConsole

// jsWasmJs
public actual class SysLog private actual constructor(min: Level): Log(SYS_LOG_UID, min) {

    public actual companion object {

        public actual val Debug: SysLog = SysLog(Level.Debug)

        public actual const val UID: String = SYS_LOG_UID

        public actual val isInstalled: Boolean get() = commonIsInstalled()

        public actual fun of(
            min: Level,
        ): SysLog = ::SysLog.commonOf(min)
    }

    actual override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        val formatted = run {
            val now = JsDate()

            // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/Date
            val dateTime = commonFormatDateTime(
                month   = now.getMonth() + 1,
                day     = now.getDate(),
                hours   = now.getHours(),
                minutes = now.getMinutes(),
                seconds = now.getSeconds(),
                millis  = now.getMilliseconds(),
            )

            commonFormatLogOrNull(level, domain, tag, msg, t, dateTime, omitLastNewLine = true)
        } ?: return false

        val consoleFn = when (level) {
            Level.Verbose,
            Level.Debug -> jsConsole.debug
            Level.Info -> jsConsole.info
            Level.Warn -> jsConsole.warn
            Level.Error,
            Level.Fatal -> jsConsole.error
        }

        @Suppress("DEPRECATION_ERROR")
        return commonLogChunk(formatted, maxLenLog = if (isNodeJs()) 8_000 else 2_000) { chunk ->
            try {
                consoleFn(chunk)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    actual override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        return super.isLoggable(level, domain, tag)
    }
}
