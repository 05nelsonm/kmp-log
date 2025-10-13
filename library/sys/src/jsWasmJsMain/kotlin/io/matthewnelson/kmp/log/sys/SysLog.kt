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
import io.matthewnelson.kmp.log.sys.internal.SYS_LOG_UID
import io.matthewnelson.kmp.log.sys.internal.commonFormat
import io.matthewnelson.kmp.log.sys.internal.commonLogChunk
import io.matthewnelson.kmp.log.sys.internal.commonOf
import io.matthewnelson.kmp.log.sys.internal.js.JsDate
import io.matthewnelson.kmp.log.sys.internal.js.formatMMddHHmmssSSS
import io.matthewnelson.kmp.log.sys.internal.js.jsConsole
import io.matthewnelson.kmp.log.sys.internal.node.IS_NODE_JS

// jsWasmJs
public actual open class SysLog private actual constructor(
    min: Level /* = Level.Verbose */,
): Log(UID, min) {

    public actual companion object Default: SysLog() {

        public actual const val UID: String = SYS_LOG_UID

        public actual fun of(
            min: Level,
        ): SysLog = ::SysLog.commonOf(min)

        private val MAX_LEN_LOG by lazy { if (IS_NODE_JS) 8_000 else 2_000 }
    }

    actual final override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        val formatted = run {
            val dateTime = JsDate().formatMMddHHmmssSSS()
            commonFormat(level, domain, tag, msg, t, dateTime, omitLastNewLine = true)
        }

        val consoleFn = when (level) {
            Level.Verbose,
            Level.Debug -> jsConsole.debug
            Level.Info -> jsConsole.info
            Level.Warn -> jsConsole.warn
            Level.Error,
            Level.Fatal -> jsConsole.error
        }

        return commonLogChunk(formatted, MAX_LEN_LOG) { chunk ->
            try {
                consoleFn(chunk)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    actual final override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        return super.isLoggable(level, domain, tag)
    }
}
