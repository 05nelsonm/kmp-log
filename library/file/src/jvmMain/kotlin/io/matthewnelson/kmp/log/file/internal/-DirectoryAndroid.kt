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

import io.matthewnelson.kmp.file.ANDROID
import io.matthewnelson.kmp.file.AccessDeniedException
import io.matthewnelson.kmp.file.ClosedException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.FileSystemException
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.NotDirectoryException
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.file.FileLog
import java.io.FileDescriptor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

internal class DirectoryOpenerAndroid private constructor(): DirectoryOpener() {

    internal companion object {

        @JvmSynthetic
        internal fun getOrNull(): DirectoryOpenerAndroid? {
            if (ANDROID.SDK_INT == null) return null

            return try {
                DirectoryOpenerAndroid()
            } catch (t: Throwable) {
                try {
                    Log.Logger.of(tag = "DirectoryOpenerAndroid", domain = FileLog.DOMAIN).w(t) {
                        "Failed to load DirectoryOpenerAndroid"
                    }
                } catch (_: Throwable) {}
                null
            }
        }
    }

    @Suppress("PrivatePropertyName")
    private val MODE_READ_ONLY: Int

    private val close: Method
    private val getFileDescriptor: Method
    private val open: Method

    override fun open(dir: File): Directory {
        var pfd: Any? = null

        val fd = try {
            pfd = open.invoke(null, dir, MODE_READ_ONLY)
            if (!dir.isDirectory) throw NotDirectoryException(dir)
            getFileDescriptor.invoke(pfd) as FileDescriptor
        } catch (t: Throwable) {
            val c = if (t is InvocationTargetException) t.cause ?: t else t
            val e: IOException = when (c) {
                is SecurityException -> AccessDeniedException(dir, reason = "SecurityException")
                    .apply { addSuppressed(c) }
                is FileSystemException -> c
                !is FileNotFoundException -> c.wrapIOException { "Failed to open directory $dir" }
                else -> run {
                    val m = c.message ?: return@run c
                    if (m.contains("denied")) {
                        return@run AccessDeniedException(dir, reason = m)
                            .apply { addSuppressed(c) }
                    }
                    if (m.contains("Is a directory")) {
                        return@run FileSystemException(dir, reason = m)
                            .apply { addSuppressed(c) }
                    }
                    c
                }
            }

            pfd?.let {
                try {
                    close.invoke(it)
                } catch (tt: Throwable) {
                    e.addSuppressed(tt)
                }
            }

            throw e
        }

        return AndroidDirectory(fd, pfd, close)
    }

    init {
        val clazz = Class.forName("android.os.ParcelFileDescriptor")

        MODE_READ_ONLY = clazz.getField("MODE_READ_ONLY").getInt(null)

        close = clazz.getMethod("close")
        getFileDescriptor = clazz.getMethod("getFileDescriptor")
        open = clazz.getMethod("open", File::class.java, Int::class.javaPrimitiveType)
    }
}

private class AndroidDirectory(fd: FileDescriptor, pfd: Any, mClose: Method): Directory() {

    private val _handle = AtomicRef<Triple<FileDescriptor, Any, Method>?>(Triple(fd, pfd, mClose))

    override fun isOpen(): Boolean = _handle._get() != null

    override fun sync() {
        val (fd, _, _) = _handle._get() ?: throw ClosedException()
        fd.sync()
    }

    override fun close() {
        val (_, pfd, mClose) = _handle._getAndSet(new = null) ?: return
        try {
            mClose.invoke(pfd)
        } catch (t: Throwable) {
            val c = if (t is InvocationTargetException) t.cause ?: t else t
            throw c.wrapIOException()
        }
    }

    override fun toString(): String = "AndroidDirectory@${hashCode()}"
}
