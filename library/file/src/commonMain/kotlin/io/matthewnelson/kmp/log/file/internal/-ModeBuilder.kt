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

import kotlin.jvm.JvmSynthetic

internal class ModeBuilder private constructor(internal val isDirectory: Boolean) {

    internal var groupRead: Boolean = false
    internal var groupWrite: Boolean = false

    internal var otherRead: Boolean = false
    internal var otherWrite: Boolean = false

    internal fun build(): String {
        val owner = mode(read = true, write = true)
        val group = mode(groupRead, groupWrite)
        val other = mode(otherRead, otherWrite)
        return "${owner}${group}${other}"
    }

    private inline fun mode(read: Boolean, write: Boolean): Int = when {
        read && write -> 6 + if (isDirectory) 1 else 0
        read -> 4 + if (isDirectory) 1 else 0
        write -> 2 + if (isDirectory) 1 else 0
        else -> 0
    }

    internal companion object {
        @JvmSynthetic
        internal fun of(isDirectory: Boolean): ModeBuilder = ModeBuilder(isDirectory)
    }
}
