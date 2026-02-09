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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.log.file.internal

import platform.posix.F_FULLFSYNC
import platform.posix.fcntl

internal actual inline fun unixFsync(fd: Int): Int {
    if (fcntl(fd, F_FULLFSYNC) != -1) return 0
    // TODO: Use kmp_file internal cinterop to check if non-local file in order to fallback to trying fsync.
    //  See: https://github.com/05nelsonm/kmp-file/blob/master/library/file/src/darwinMain/kotlin/io/matthewnelson/kmp/file/internal/DarwinFileStream.kt
    return -1
}
