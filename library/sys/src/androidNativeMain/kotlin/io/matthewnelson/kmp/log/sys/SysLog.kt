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

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.sys.internal.SYS_LOG_UID
import io.matthewnelson.kmp.log.sys.internal.androidDomainTag
import io.matthewnelson.kmp.log.sys.internal.androidLogChunk
import io.matthewnelson.kmp.log.sys.internal.commonDomainTag
import io.matthewnelson.kmp.log.sys.internal.commonIsInstalled
import io.matthewnelson.kmp.log.sys.internal.commonOf
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import platform.android.ANDROID_LOG_DEBUG
import platform.android.ANDROID_LOG_ERROR
import platform.android.ANDROID_LOG_FATAL
import platform.android.ANDROID_LOG_INFO
import platform.android.ANDROID_LOG_VERBOSE
import platform.android.ANDROID_LOG_WARN
import platform.android.__android_log_print
import platform.posix.RTLD_NEXT
import platform.posix.android_get_device_api_level
import platform.posix.dlsym

// androidNative
public actual open class SysLog private actual constructor(
    min: Level /* = Level.Debug */,
): Log(UID, min) {

    public actual companion object Default: SysLog() {

        public actual const val UID: String = SYS_LOG_UID

        public actual val isInstalled: Boolean get() = commonIsInstalled()

        public actual fun of(
            min: Level,
        ): SysLog = ::SysLog.commonOf(min)

        // Exposed for testing
        @OptIn(ExperimentalForeignApi::class)
        internal fun isLoggableOrNull(level: Level, domain: String?, tag: String): Boolean? {
            val ___android_log_is_loggable = ANDROID_LOG_IS_LOGGABLE ?: return null
            val priority = level.toPriority()
            // Do not need to use androidDomainTag b/c __android_log_is_loggable is only available
            // from API 30+, so no need to check device API level as the limitation on tag length
            // was removed in API 26.
            // TODO: Should `null` be passed for domain here and only check for tag?
            val _tag = commonDomainTag(domain, tag).cstr
            return ___android_log_is_loggable.invoke(priority, _tag, priority) == 1
        }

        private fun Level.toPriority(): Int = when (this) {
            Level.Verbose -> ANDROID_LOG_VERBOSE
            Level.Debug -> ANDROID_LOG_DEBUG
            Level.Info -> ANDROID_LOG_INFO
            Level.Warn -> ANDROID_LOG_WARN
            Level.Error -> ANDROID_LOG_ERROR
            Level.Fatal -> ANDROID_LOG_FATAL
        }.toInt()

        // Normally one would not want to hold onto a function pointer reference
        // statically, but it's from glibc which is not going to be hot reloaded
        // or anything w/o this process terminating, so.
        //
        // Available for API 30+
        // include/android/log.h
        @OptIn(ExperimentalForeignApi::class)
        private val ANDROID_LOG_IS_LOGGABLE by lazy {
            val ptr = dlsym(RTLD_NEXT, "__android_log_is_loggable")
                ?: return@lazy null

            @Suppress("UNCHECKED_CAST")
            ptr as CPointer<CFunction<(
                __prio: Int,
                __tag: CValuesRef<ByteVar>?,
                __default_prio: Int,
            ) -> Int>>
        }
    }

    actual final override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        val priority = level.toPriority()
        val _tag = androidDomainTag(android_get_device_api_level(), domain, tag)
        return androidLogChunk(msg, t) { chunk ->
            __android_log_print(priority, _tag, chunk) > 0
        }
    }

    actual final override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        return isLoggableOrNull(level, domain, tag) ?: true
    }
}
