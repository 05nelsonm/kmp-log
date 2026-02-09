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
import io.matthewnelson.kmp.file.ClosedException
import io.matthewnelson.kmp.file.DirectoryNotEmptyException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileAlreadyExistsException
import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.FileSystemException
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.NotDirectoryException
import io.matthewnelson.kmp.file.SysFsInfo
import io.matthewnelson.kmp.file.wrapIOException
import java.nio.channels.FileChannel
import java.nio.file.InvalidPathException
import java.nio.file.StandardOpenOption

@Suppress("NewApi")
internal class DirectoryOpenerNioPosix private constructor(): DirectoryOpener() {

    internal companion object {

        // MUST check for existence of java.nio.file.Files
        // class first before referencing, otherwise Android
        // may throw a VerifyError.
        @JvmSynthetic
        @Throws(VerifyError::class)
        internal fun getOrNull(): DirectoryOpenerNioPosix? {
            // Windows
            if (SysFsInfo.name == "FsJvmNioNonPosix") return null
            return DirectoryOpenerNioPosix()
        }
    }

    override fun open(dir: File): Directory {
        val path = try {
            dir.toPath()
        } catch (e: InvalidPathException) {
            throw e.wrapIOException()
        }

        var ch: FileChannel? = null
        try {
            ch = FileChannel.open(path, StandardOpenOption.READ)
            if (!dir.isDirectory) throw NotDirectoryException(dir)
        } catch (t: Throwable) {
            val e: IOException = when (t) {
                is SecurityException -> AccessDeniedException(dir, reason = "SecurityException")
                    .apply { addSuppressed(t) }
                is FileSystemException -> t
                is java.nio.file.FileSystemException -> when (t) {
                    is java.nio.file.AccessDeniedException -> AccessDeniedException(dir, reason = t.reason)
                    is java.nio.file.DirectoryNotEmptyException -> DirectoryNotEmptyException(dir)
                    is java.nio.file.FileAlreadyExistsException -> FileAlreadyExistsException(dir, reason = t.reason)
                    is java.nio.file.NoSuchFileException -> FileNotFoundException(t.message)
                    is java.nio.file.NotDirectoryException -> NotDirectoryException(dir)
                    else -> FileSystemException(dir, reason = t.reason)
                }.apply { addSuppressed(t) }
                else -> t.wrapIOException { "Failed to open directory $dir" }
            }
            try {
                ch?.close()
            } catch (ee: IOException) {
                e.addSuppressed(ee)
            }
            throw e
        }

        return NioPosixDirectory(ch)
    }
}

private class NioPosixDirectory(ch: FileChannel): Directory() {

    private val _ch = AtomicRef<FileChannel?>(ch)

    override fun isOpen(): Boolean = _ch._get()?.isOpen ?: false

    override fun sync() {
        val ch = _ch._get() ?: throw ClosedException()
        ch.force(/* metaData */ true)
    }

    override fun close() {
        val ch = _ch._getAndSet(new = null) ?: return
        ch.close()
    }

    override fun toString(): String = "NioPosixDirectory@${hashCode()}"
}
