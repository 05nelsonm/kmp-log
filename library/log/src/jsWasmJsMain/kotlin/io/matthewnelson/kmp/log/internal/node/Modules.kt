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
package io.matthewnelson.kmp.log.internal.node

import io.matthewnelson.kmp.log.Log

internal val IS_NODE_JS: Boolean by lazy { isNodeJsInternal() }

internal const val CODE_IS_NODE_JS: String =
"""
(typeof process !== 'undefined' 
    && process.versions != null 
    && process.versions.node != null) ||
(typeof window !== 'undefined' 
    && typeof window.process !== 'undefined' 
    && window.process.versions != null 
    && window.process.versions.node != null)
"""

internal const val CODE_MODULE_PROCESS: String = "eval('require')('process')"

internal expect fun isNodeJsInternal(): Boolean
internal expect fun nodeModuleProcess(): ModuleProcess

// For SysLog, and hidden behind Log.Root context
@Deprecated("For internal use only. Do not use.", level = DeprecationLevel.ERROR)
public fun Log.Root.isNodeJs(): Boolean = IS_NODE_JS
