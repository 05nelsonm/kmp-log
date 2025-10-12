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
@file:Suppress("LocalVariableName", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.log.sys.internal

import io.matthewnelson.kmp.log.sys.SysLog

internal inline fun SysLog.Companion.androidDomainTag(
    DEVICE_SDK_INT: Int,
    domain: String?,
    tag: String,
): String = when {
    DEVICE_SDK_INT >= 26 -> commonDomainTag(domain, tag)
    tag.length <= 23 -> tag
    else -> tag.take(23)
}
