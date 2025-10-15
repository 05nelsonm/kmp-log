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
package io.matthewnelson.kmp.log.internal

import io.matthewnelson.kmp.log.Log

// NOTE: Never modify. If so, update Log.AbortHandler.UID documentation.
internal const val ABORT_HANDLER_UID: String = "io.matthewnelson.kmp.log.Log.AbortHandler"

internal expect val ABORTER_ACCEPTS_MESSAGE: Boolean

internal expect inline fun Log.AbortHandler.doAbort(t: Throwable?): Boolean
