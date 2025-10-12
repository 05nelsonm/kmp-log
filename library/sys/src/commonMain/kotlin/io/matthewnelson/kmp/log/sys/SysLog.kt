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

/**
 * TODO
 * */
public expect class SysLog: Log {

    /**
     * TODO
     * */
    public constructor()

    /**
     * TODO
     * */
    public constructor(min: Level)

    override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean

    public companion object {

        /**
         * The [SysLog.uid] (i.e. `io.matthewnelson.kmp.log.sys.SysLog`).
         *
         * Can be used with [Log.Root.uninstall].
         * */
        public /* const */ val UID: String /* = "io.matthewnelson.kmp.log.sys.SysLog" */
    }
}
