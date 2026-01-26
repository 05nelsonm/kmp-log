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

import io.matthewnelson.kmp.file.DirectoryNotEmptyException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.path
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.EEXIST
import platform.posix.EINTR
import platform.posix.EISDIR
import platform.posix.ENOENT
import platform.posix.ENOTDIR
import platform.posix.ENOTEMPTY
import platform.posix.errno
import platform.posix.rename

@Throws(IOException::class)
@OptIn(ExperimentalForeignApi::class)
internal actual fun File.moveLogTo(dest: File) {
    if (doRename(dest) == 0) return

    throw when (val err = errno) {
        // Source does not exist.
        ENOENT -> errnoToIOException(err, this, other = null)
        // Source exists and is not a directory. Dest exists and is a directory.
        EISDIR -> try {
            // Potentially malicious behavior. Should be a regular file from
            // a prior move; try deleting. DirectoryNotEmptyException will be
            // thrown for us if unable to delete.
            dest.delete2()
            if (doRename(dest) == 0) return // Success

            // 2nd rename failed; throw so we can add original as suppressed.
            throw errnoToIOException(errno, this, dest)
        } catch (e: IOException) {
            val original = errnoToIOException(err, dest, other = null)
            e.addSuppressed(original)
            e
        }
        // Source exists and is directory. Dest exists and is not a directory.
        ENOTDIR -> errnoToIOException(err, dest, other = null)
        // Dest exists and is a non-empty directory.
        ENOTEMPTY, EEXIST -> DirectoryNotEmptyException(dest)
        else -> errnoToIOException(err, this, dest)
    }
}

private inline fun File.doRename(dest: File): Int {
    var ret: Int
    do {
        ret = rename(this.path, dest.path)
    } while (ret == -1 && errno == EINTR)
    return ret
}
