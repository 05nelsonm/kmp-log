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
import io.matthewnelson.kmp.log.sys.internal.commonOf
import io.matthewnelson.kmp.log.sys.internal.jvmLogPrint

// android
public actual open class SysLog private actual constructor(
    min: Level /* = Level.Verbose */,
): Log(UID, min) {

    public actual companion object Default: SysLog() {

        public actual const val UID: String = SYS_LOG_UID

        @JvmStatic
        public actual fun of(
            min: Level,
        ): SysLog = ::SysLog.commonOf(min)

        // Exposed for testing
        @JvmSynthetic
        internal fun isLoggableOrNull(level: Level, domain: String?, tag: String): Boolean? {
            if (Build.VERSION.SDK_INT <= 0) return null
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
    }

    actual override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        if (Build.VERSION.SDK_INT <= 0) {
            // Android Unit Tests
            return jvmLogPrint(level, domain, tag, msg, t)
        }

        val priority = level.toPriority()
        val _tag = androidDomainTag(Build.VERSION.SDK_INT, domain, tag)
        return androidLogChunk(msg, t) { chunk ->
            android.util.Log.println(priority, _tag, chunk) > 0
        }
    }

    actual final override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        return isLoggableOrNull(level, domain, tag) ?: true
    }
}
