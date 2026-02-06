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

import io.matthewnelson.kmp.file.AccessDeniedException
import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.file.ClosedException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileSystemException
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.lastErrorToIOException
import io.matthewnelson.kmp.file.path
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.windows.CloseHandle
import platform.windows.CreateFileA
import platform.windows.ERROR_INVALID_PARAMETER
import platform.windows.ERROR_LOCK_VIOLATION
import platform.windows.ERROR_NOT_LOCKED
import platform.windows.FALSE
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetLastError
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OPEN_ALWAYS
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

internal actual abstract class LockFile private constructor(h: HANDLE?): Closeable {

    internal constructor(): this(h = null)

    @Volatile
    private var _h: HANDLE? = h
    private val closeLock = SynchronizedObject()

    internal actual fun isOpen(): Boolean = _h != null

    @Throws(IllegalArgumentException::class, IOException::class)
    internal open fun lock(position: Long, size: Long, blocking: Boolean): FileLock? {
        val h = _h ?: throw ClosedException()
        val ret = kmp_log_file_setlk(
            h = h,
            position = position.toULong(),
            length = size.toULong(),
            locking = 1,
            blocking = if (blocking) 1 else 0,
            exclusive = 1,
        )
        if (ret == 0) return fileLock(_position = position, _size = size)

        val error = GetLastError().toInt()

        if (!blocking && error == ERROR_LOCK_VIOLATION) return null

        val ioException = lastErrorToIOException(error.toUInt())
        throw if (error == ERROR_INVALID_PARAMETER) {
            IllegalArgumentException(ioException.message)
        } else {
            ioException
        }
    }

    actual final override fun close() {
        val h = synchronized(closeLock) {
            val h = _h ?: return
            _h = null
            h
        }
        if (CloseHandle(h) == FALSE) throw lastErrorToIOException()
    }

    internal companion object {

        @Throws(IOException::class)
        internal fun open(file: File): LockFile {
            val handle = openHandle(file)
            return object : LockFile(handle) {}
        }

        // Exposed for testing
        @Throws(IOException::class)
        internal inline fun openHandle(file: File): HANDLE {
            val handle = CreateFileA(
                lpFileName = file.path,
                dwDesiredAccess = (GENERIC_READ.toInt() or GENERIC_WRITE).convert(),
                dwShareMode = (FILE_SHARE_DELETE or FILE_SHARE_READ or FILE_SHARE_WRITE).convert(),
                lpSecurityAttributes = null,
                dwCreationDisposition = OPEN_ALWAYS.convert(),
                dwFlagsAndAttributes = FILE_ATTRIBUTE_NORMAL.convert(),
                hTemplateFile = null,
            )
            if (handle == null || handle == INVALID_HANDLE_VALUE) {
                val e = lastErrorToIOException(file)
                if (e !is AccessDeniedException) throw e

                val isDirectory = try {
                    file.isDirectory()
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                    throw e
                }
                if (!isDirectory) throw e

                val ee = FileSystemException(file, reason = "Is a directory")
                ee.addSuppressed(e)
                throw ee
            }
            return handle
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
                val h = _h ?: throw ClosedException()
                kmp_log_file_setlk(
                    h = h,
                    position = position().toULong(),
                    length = size().toULong(),
                    locking = 0,
                    blocking = -1, // ignored
                    exclusive = -1, // ignored
                )
            }
            if (ret == 0) return

            val error = GetLastError()
            if (error.toInt() == ERROR_NOT_LOCKED) return
            throw lastErrorToIOException(error)
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
