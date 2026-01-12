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
import io.matthewnelson.kmp.file.lastErrorToIOException
import io.matthewnelson.kmp.file.path
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.windows.CloseHandle
import platform.windows.CreateFileA
import platform.windows.FALSE
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OPEN_ALWAYS
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

internal actual abstract class LockFile private constructor(h: HANDLE): Closeable {

    @Volatile
    private var _h: HANDLE? = h
    private val closeLock = SynchronizedObject()

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
            if (handle == null || handle == INVALID_HANDLE_VALUE) throw lastErrorToIOException(file)
            return handle
        }
    }
}
