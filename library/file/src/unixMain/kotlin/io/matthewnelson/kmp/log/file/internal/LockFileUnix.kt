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
import io.matthewnelson.kmp.file.ClosedException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.path
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.EACCES
import platform.posix.EAGAIN
import platform.posix.EINTR
import platform.posix.EINVAL
import platform.posix.EOVERFLOW
import platform.posix.EWOULDBLOCK
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.S_IRGRP
import platform.posix.S_IROTH
import platform.posix.S_IRUSR
import platform.posix.S_IWGRP
import platform.posix.S_IWOTH
import platform.posix.S_IWUSR
import platform.posix.errno
import kotlin.concurrent.Volatile

@Throws(IOException::class)
internal actual inline fun File.openLockFile(): LockFile = LockFile.open(this)

@Suppress("UNUSED")
@Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
internal actual inline fun LockFile.lock(position: Long, size: Long): FileLock {
    return lock(position, size, blocking = true)!!
}

@Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
internal actual inline fun LockFile.tryLock(position: Long, size: Long): FileLock? {
    return lock(position, size, blocking = false)
}

internal actual abstract class LockFile private constructor(fd: Int): Closeable {

    internal constructor(): this(fd = -1)

    @Volatile
    private var _fd: Int = fd
    private val closeLock = SynchronizedObject()

    internal actual fun isOpen(): Boolean = _fd != -1

    @Throws(IllegalArgumentException::class, IOException::class)
    internal open fun lock(position: Long, size: Long, blocking: Boolean): FileLock? {
        val fd = _fd
        if (fd == -1) throw ClosedException()
        val ret = kmp_log_file_setlk(
            fd = fd,
            position = position,
            length = size,
            locking = 1,
            blocking = if (blocking) 1 else 0,
            exclusive = 1,
        )
        if (ret == 0) return fileLock(_position = position, _size = size)

        val error = errno

        @Suppress("DUPLICATE_LABEL_IN_WHEN")
        if (!blocking) when (error) {
            // EACCES is synonymous to EAGAIN here for what the operation
            // is. It's possible on some systems when the file being locked
            // is on an NFS mounted drive.
            EACCES, EAGAIN, EWOULDBLOCK -> return null
        }

        val ioException = errnoToIOException(error)
        throw when (error) {
            EINVAL, EOVERFLOW -> IllegalArgumentException(ioException.message)
            else -> ioException
        }
    }

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
            // No O_TRUNC because if a lock is held by another process, it could cause problems.
            val flags = O_RDWR or O_CREAT or O_CLOEXEC
            val mode = S_IRUSR or S_IWUSR or S_IRGRP or S_IWGRP or S_IROTH or S_IWOTH // mode: 666
            var fd: Int
            do {
                fd = platform.posix.open(file.path, flags, mode)
            } while (fd == -1 && errno == EINTR)
            if (fd == -1) throw errnoToIOException(errno, file)
            return fd
        }
    }

    @Suppress("LocalVariableName")
    private fun fileLock(_position: Long, _size: Long) = object : FileLock(_position, _size) {

        @Volatile
        private var isReleased = false

        override fun isValid(): Boolean = isOpen() && !isReleased

        override fun release() {
            if (isReleased) return

            val ret = synchronized(closeLock) {
                if (isReleased) return
                isReleased = true
                val fd = _fd
                if (fd == -1) throw ClosedException()
                kmp_log_file_setlk(
                    fd = fd,
                    position = position(),
                    length = size(),
                    locking = 0,
                    blocking = -1, // ignored
                    exclusive = -1, // ignored
                )
            }
            if (ret != 0) throw errnoToIOException(errno)
        }
    }

    override fun toString(): String = "LockFile@" + hashCode()
}

internal actual object StubLockFile: LockFile() {

    override fun lock(position: Long, size: Long, blocking: Boolean): StubFileLock {
        if (size == FILE_LOCK_SIZE) {
            if (position == FILE_LOCK_POS_LOG) return LockLog
            if (position == FILE_LOCK_POS_ROTATE) return LockRotate
        }
        return StubFileLock(position, size)
    }

    private val LockLog by lazy {
        StubFileLock(FILE_LOCK_POS_LOG, FILE_LOCK_SIZE)
    }

    private val LockRotate by lazy {
        StubFileLock(FILE_LOCK_POS_ROTATE, FILE_LOCK_SIZE)
    }

    actual override fun toString(): String = "StubLockFile"
}
