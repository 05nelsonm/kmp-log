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
import io.matthewnelson.kmp.log.sys.internal.nativeIsLoggable
import io.matthewnelson.kmp.log.sys.internal.nativeLogPrint
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

// darwin
public actual open class SysLog private actual constructor(
    min: Level /* = Level.Debug */,
): Log(UID, min) {

    public actual companion object Default: SysLog() {

        public actual const val UID: String = SYS_LOG_UID

        public actual fun of(
            min: Level,
        ): SysLog = ::SysLog.commonOf(min)

        private val SYS_LOG_TIME_FORMAT = NSDateFormatter().apply { dateFormat = "MM-dd HH:mm:ss.SSS" }
    }

    actual final override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        return nativeLogPrint(level, domain, tag, msg, t, _dateTime = {
            val now = NSDate()
            SYS_LOG_TIME_FORMAT.stringFromDate(now)
        })
    }

    actual final override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        return nativeIsLoggable(level)
    }
}
