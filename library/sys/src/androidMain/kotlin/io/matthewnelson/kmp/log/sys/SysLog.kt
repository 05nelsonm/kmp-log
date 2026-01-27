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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "LocalVariableName")

package io.matthewnelson.kmp.log.sys

import android.os.Build
import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.sys.internal.SYS_LOG_UID
import io.matthewnelson.kmp.log.sys.internal.androidDomainTag
import io.matthewnelson.kmp.log.sys.internal.androidLogChunk
import io.matthewnelson.kmp.log.sys.internal.commonIsInstalled
import io.matthewnelson.kmp.log.sys.internal.commonOf
import io.matthewnelson.kmp.log.sys.internal.jvmLogPrint

// android
public actual class SysLog private actual constructor(min: Level): Log(SYS_LOG_UID, min) {

    public actual companion object {

        @JvmField
        public actual val Debug: SysLog = SysLog(Level.Debug)

        public actual const val UID: String = SYS_LOG_UID

        @JvmStatic
        @get:JvmName("isInstalled")
        public actual val isInstalled: Boolean get() = commonIsInstalled()

        @JvmStatic
        public actual fun of(
            min: Level,
        ): SysLog = ::SysLog.commonOf(min)

        // Exposed for testing
        @JvmSynthetic
        internal fun isLoggableOrDefault(level: Level, domain: String?, tag: String, default: Boolean): Boolean {
            if (Build.VERSION.SDK_INT <= 0) return default
            // TODO: Should `null` be passed for domain here and only check for tag?
            val _tag = androidDomainTag(Build.VERSION.SDK_INT, domain, tag)
            return android.util.Log.isLoggable(_tag, level.toPriority())
        }

        private fun Level.toPriority(): Int = when (this) {
            Level.Verbose -> android.util.Log.VERBOSE
            Level.Debug -> android.util.Log.DEBUG
            Level.Info -> android.util.Log.INFO
            Level.Warn -> android.util.Log.WARN
            Level.Error -> android.util.Log.ERROR
            Level.Fatal -> android.util.Log.ASSERT
        }

        private const val MAX_LEN_LOG: Int = 4_000
    }

    actual override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        if (Build.VERSION.SDK_INT <= 0) {
            // Android Unit Tests
            return jvmLogPrint(level, domain, tag, msg, t)
        }

        val priority = level.toPriority()
        val _tag = androidDomainTag(Build.VERSION.SDK_INT, domain, tag)
        return androidLogChunk(msg, t, MAX_LEN_LOG) { chunk ->
            android.util.Log.println(priority, _tag, chunk) > 0
        }
    }

    actual override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        return isLoggableOrDefault(level, domain, tag, default = true)
    }
}
