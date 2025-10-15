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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.log.internal

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.internal.node.IS_NODE_JS
import io.matthewnelson.kmp.log.internal.node.nodeModuleProcess

internal actual val ABORTER_ACCEPTS_MESSAGE: Boolean = !IS_NODE_JS // Throw exception on JsBrowser

internal actual inline fun Log.AbortHandler.doAbort(t: Throwable?): Boolean {
    if (IS_NODE_JS) {
        nodeModuleProcess().abort()
    } else {
        throw (t ?: Throwable("ABORT"))
    }
    return false
}
