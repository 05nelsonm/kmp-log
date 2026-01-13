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

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.file.ANDROID
import io.matthewnelson.kmp.log.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal actual inline fun Log.Root.isDesktop(): Boolean = ANDROID.SDK_INT == null

private val LOG_TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ENGLISH)

internal actual fun Log.Root.now(): String {
    val now = Date(System.currentTimeMillis())
    return LOG_TIME_FORMAT.format(now)
}
