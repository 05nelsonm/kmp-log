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
import io.matthewnelson.kmp.log.sys.internal.commonOf
import io.matthewnelson.kmp.log.sys.internal.jvmLogPrint

// jvm
public actual open class SysLog private actual constructor(
    min: Level /* = Level.Debug */,
): Log(UID, min) {

    public actual companion object Default: SysLog() {

        public actual const val UID: String = SYS_LOG_UID

        @JvmStatic
        public actual fun of(
            min: Level,
        ): SysLog = ::SysLog.commonOf(min)
    }

    actual final override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        return jvmLogPrint(level, domain, tag, msg, t)
    }

    actual final override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        return super.isLoggable(level, domain, tag)
    }
}
