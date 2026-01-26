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
package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.file.AccessDeniedException
import io.matthewnelson.kmp.file.DirectoryNotEmptyException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.FileStream
import io.matthewnelson.kmp.file.FileSystemException
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.NotDirectoryException
import io.matthewnelson.kmp.file.OpenExcl
import io.matthewnelson.kmp.file.chmod2
import io.matthewnelson.kmp.file.exists2
import io.matthewnelson.kmp.file.openRead
import io.matthewnelson.kmp.file.openReadWrite

@Throws(IOException::class)
internal fun File.openLogFileRobustly(mode: String): FileStream.ReadWrite = try {
    openReadWrite(excl = OpenExcl.MaybeCreate.of(mode))
} catch (e: AccessDeniedException) {
    try {
        chmod2(mode)
    } catch (ee: IOException) {
        e.addSuppressed(ee)
        throw e
    }
    try {
        openReadWrite(excl = OpenExcl.MaybeCreate.of(mode))
    } catch (ee: IOException) {
        e.addSuppressed(ee)
        throw e
    }
}

// NOTE: Do NOT use on FileLog.dotLockFile
internal fun File.exists2Robustly(): Boolean {
    try {
        if (exists2()) return true
    } catch (_: IOException) {
        var s: FileStream.Read? = null
        return try {
            // Must exist to open O_RDONLY.
            s = openRead()
            true
        } catch (t: Throwable) {
            t.message?.contains("Is a directory", ignoreCase = true) == true
        } finally {
            try {
                s?.close()
            } catch (_: Throwable) {}
        }
    }
    return false
}

/**
 * Move a log file, taking the place of [dest]
 *
 * TODO: Promote to kmp-file >> https://github.com/05nelsonm/kmp-file/issues/209
 *
 * @param [dest] The destination to move the file to.
 *
 * @throws [FileNotFoundException] If source [File] does not exist.
 * @throws [DirectoryNotEmptyException] If [dest] is an existing non-empty directory.
 * @throws [NotDirectoryException] If source [File] is an existing directory, but
 *   [dest] exists and is not a directory.
 * @throws [FileSystemException] Otherwise.
 * */
@Throws(IOException::class)
internal expect fun File.moveLogTo(dest: File)
