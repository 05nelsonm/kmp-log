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
package io.matthewnelson.kmp.log

open class TestLog(
    uid: String,
    min: Level = Level.Verbose,
    max: Level = Level.Fatal,
): Log(uid, min, max) {

    class LogItem(
        val level: Level,
        val domain: String?,
        val tag: String,
        val msg: String?,
        val t: Throwable?,
    )

    private var _logs: ArrayList<LogItem>? = null
    val logs: List<LogItem>? get() = _logs?.toList()

    final override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        return _logs?.add(LogItem(level, domain, tag, msg, t)) ?: false
    }

    override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        return tag != "Log.Root"
    }

    override fun onInstall() {
        check(_logs == null) { "_logs != null" }
        _logs = ArrayList(50)
    }

    override fun onUninstall() {
        _logs = null
    }
}
