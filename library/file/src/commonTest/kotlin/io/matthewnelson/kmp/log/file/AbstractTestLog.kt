/*
 * Copyright (c) 2026 Matthew Nelson
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
package io.matthewnelson.kmp.log.file

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.file.internal.CurrentThread
import io.matthewnelson.kmp.log.file.internal.format
import io.matthewnelson.kmp.log.file.internal.id
import io.matthewnelson.kmp.log.file.internal.now
import io.matthewnelson.kmp.log.file.internal.pid

internal abstract class AbstractTestLog(uid: String): Log(uid, min = Level.Verbose) {

    override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        val time = FileLog.now(omitYear = true)
        val pid = FileLog.pid()
        val tid = CurrentThread.id()
        val formatted = FileLog.format(time, pid, tid, level, domain, tag, msg, t) ?: return false
        print(formatted)
        return true
    }

    override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        return domain == FileLog.DOMAIN
    }
}
