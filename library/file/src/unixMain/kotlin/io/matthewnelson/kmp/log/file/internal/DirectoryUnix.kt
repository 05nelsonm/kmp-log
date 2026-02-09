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

import io.matthewnelson.kmp.file.ClosedException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.path
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.EINTR
import platform.posix.O_CLOEXEC
import platform.posix.O_DIRECTORY
import platform.posix.O_RDONLY
import platform.posix.errno
import kotlin.concurrent.AtomicInt

@Throws(IOException::class)
@OptIn(ExperimentalForeignApi::class)
internal actual fun File.openDirectory(): Directory {
    val flags = O_RDONLY or O_CLOEXEC or O_DIRECTORY
    var fd: Int
    do {
        fd = platform.posix.open(path, flags, 0)
    } while (fd == -1 && errno == EINTR)
    if (fd == -1) throw errnoToIOException(errno, this)
    return UnixDirectory(fd)
}

@OptIn(ExperimentalForeignApi::class)
private class UnixDirectory(fd: Int): Directory() {

    private val _fd = AtomicInt(fd)

    override fun isOpen(): Boolean = _fd.value != -1

    override fun sync() {
        var ret: Int
        do {
            val fd = _fd.value
            if (fd == -1) throw ClosedException()
            ret = unixFsync(fd)
        } while (ret == -1 && errno == EINTR)
        if (ret == -1) throw errnoToIOException(errno)
    }

    override fun close() {
        val fd = _fd.getAndSet(-1)
        if (fd == -1) return
        if (platform.posix.close(fd) == 0) return
        throw errnoToIOException(errno)
    }

    override fun toString(): String = "UnixDirectory@${hashCode()}"
}

internal expect inline fun unixFsync(fd: Int): Int
