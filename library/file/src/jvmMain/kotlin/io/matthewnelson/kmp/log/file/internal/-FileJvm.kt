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
import io.matthewnelson.kmp.file.FileSystemException
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.NotDirectoryException
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.exists2

@Throws(IOException::class)
internal actual fun File.moveLogTo(dest: File) {
    try {
        // As much as I'd prefer calling renameTo and reacting to the
        // failure, we must check if the source File is NOT a regular
        // file. File.renameTo can overwrite a regular file with a
        // directory which we do NOT want to do.
        //
        // This non-atomic implementation should be OK here though, as
        // moveLogTo is only utilized for log file rotation and is always
        // called while holding a FileLock (other processes using FileLog
        // will not be executing a rotation at the same time).
        if (!isFile) {
            if (!exists2()) throw FileNotFoundException(toString())
            if (dest.isFile) throw NotDirectoryException(dest)

            // dest is either NOT a regular file, or does not exist.
        }

        if (renameTo(dest)) return
    } catch (t: Throwable) {
        if (t is SecurityException) {
            val e = AccessDeniedException(this, dest, "SecurityException")
            e.addSuppressed(t)
            throw e
        }
        if (t is FileNotFoundException) throw t
        if (t is FileSystemException) throw t

        val e = FileSystemException(this, dest, "renameTo failure")
        e.addSuppressed(t)
        throw e
    }

    // At this point, source File is a regular file and exists, otherwise
    // a FileNotFoundException would have already been thrown. Destination
    // must be a directory, or we're on Windows and it exists. Either way,
    // try to delete it and retry File.renameTo.
    val t = try {
        dest.delete2(ignoreReadOnly = true, mustExist = false)
        if (renameTo(dest)) return
        null
    } catch (t: Throwable) {
        if (t is DirectoryNotEmptyException) throw t
        t
    }

    val e = FileSystemException(this, dest, "renameTo failure")
    t?.let { e.addSuppressed(t) }
    throw e
}
