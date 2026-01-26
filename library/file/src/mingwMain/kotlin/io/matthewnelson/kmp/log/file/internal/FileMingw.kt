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

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.NotDirectoryException
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.lastErrorToIOException
import io.matthewnelson.kmp.file.path
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.EINTR
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix._stat64
import platform.posix.errno
import platform.windows.ERROR_ACCESS_DENIED
import platform.windows.GetLastError
import platform.windows.MOVEFILE_REPLACE_EXISTING
import platform.windows.MoveFileExA

@Throws(IOException::class)
@OptIn(ExperimentalForeignApi::class)
internal actual fun File.moveLogTo(dest: File) {
    if (doMove(dest) != 0) return

    val error = GetLastError()
    if (error.toInt() == ERROR_ACCESS_DENIED) memScoped {
        // Source exists and:
        //  - Is a directory, while Dest exists and is NOT a directory.
        //  - Is not a directory, while Dest exists and IS a directory.
        val sourceIsDir = try {
            isDirectory(this@moveLogTo)
        } catch (e: IOException) {
            val ee = lastErrorToIOException(this@moveLogTo, dest, error)
            e.addSuppressed(ee)
            throw e
        }
        val destIsDir = try {
            isDirectory(dest)
        } catch (e: IOException) {
            val ee = lastErrorToIOException(this@moveLogTo, dest, error)
            e.addSuppressed(ee)
            throw e
        }

        // EISDIR
        if (!sourceIsDir && destIsDir) try {
            // Potential malicious behavior. Should be a regular file from
            // a prior move; try deleting. DirectoryNotEmptyException will
            // be thrown for us if unable to delete.
            dest.delete2(ignoreReadOnly = true)
            if (doMove(dest) != 0) return // Success

            // 2nd rename failed; throw so we can add original as suppressed
            throw lastErrorToIOException(this@moveLogTo, dest)
        } catch (e: IOException) {
            val original = lastErrorToIOException(this@moveLogTo, dest, error)
            e.addSuppressed(original)
            throw e
        }

        // ENOTDIR
        if (sourceIsDir && !destIsDir) {
            val e = NotDirectoryException(dest)
            val ee = lastErrorToIOException(this@moveLogTo, dest, error)
            e.addSuppressed(ee)
            throw e
        }
        // Fall through
    }

    throw lastErrorToIOException(this, dest, error)
}

private inline fun File.doMove(dest: File): Int = MoveFileExA(
    lpExistingFileName = this.path,
    lpNewFileName = dest.path,
    dwFlags = MOVEFILE_REPLACE_EXISTING.toUInt(),
)

@Throws(IOException::class)
@OptIn(ExperimentalForeignApi::class)
private inline fun MemScope.isDirectory(file: File): Boolean {
    val stat = alloc<_stat64>()
    var ret: Int
    do {
        ret = _stat64(file.path, stat.ptr)
    } while (ret == -1 && errno != EINTR)
    if (ret == -1) throw errnoToIOException(errno, file)
    return (stat.st_mode.toInt() and S_IFMT) == S_IFDIR
}
