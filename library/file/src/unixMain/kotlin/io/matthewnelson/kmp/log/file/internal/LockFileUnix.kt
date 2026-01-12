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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE")
@file:OptIn(ExperimentalForeignApi::class)

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.path
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.EINTR
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.S_IRUSR
import platform.posix.S_IWUSR
import platform.posix.errno
import kotlin.concurrent.Volatile

@Throws(IOException::class)
internal actual inline fun File.openLockFile(): LockFile = LockFile.open(this)

@Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
internal actual inline fun LockFile.lock(position: Long, size: Long): FileLock {
    TODO("")
}

@Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
internal actual inline fun LockFile.tryLock(position: Long, size: Long): FileLock? {
    TODO("")
}

internal actual abstract class LockFile private constructor(fd: Int): Closeable {

    @Volatile
    private var _fd: Int = fd
    private val closeLock = SynchronizedObject()

    actual final override fun close() {
        val fd = synchronized(closeLock) {
            val fd = _fd
            if (fd == -1) return
            _fd = -1
            fd
        }
        if (platform.posix.close(fd) != 0) throw errnoToIOException(errno)
    }

    internal companion object {

        @Throws(IOException::class)
        internal fun open(file: File): LockFile {
            val fd = openFd(file)
            return object : LockFile(fd) {}
        }

        // Exposed for testing
        @Throws(IOException::class)
        internal inline fun openFd(file: File): Int {
            // No O_TRUNC because if a lock is held by another
            // process, it could cause problems.
            val flags = O_RDWR or O_CREAT or O_CLOEXEC
            val mode = S_IRUSR or S_IWUSR // 600
            var fd: Int
            do {
                fd = platform.posix.open(file.path, flags, mode)
            } while (fd == -1 && errno == EINTR)
            if (fd == -1) throw errnoToIOException(errno, file)
            return fd
        }
    }
}
