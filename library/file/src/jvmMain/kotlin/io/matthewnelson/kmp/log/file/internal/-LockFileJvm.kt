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

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.file.AccessDeniedException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.wrapIOException
import java.io.RandomAccessFile

@Suppress("ACTUAL_WITHOUT_EXPECT", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
internal actual typealias FileLock = java.nio.channels.FileLock
@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias LockFile = java.nio.channels.FileChannel

@Throws(IOException::class)
internal actual inline fun File.openLockFile(): LockFile = openNioFileChannel()

@Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
internal actual inline fun LockFile.lock(position: Long, size: Long): FileLock {
    return lock(position, size, /* shared */ false)
}

@Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
internal actual inline fun LockFile.tryLock(position: Long, size: Long): FileLock? {
    return tryLock(position, size, /* shared */ false)
}

@Throws(IOException::class)
internal fun File.openNioFileChannel(): LockFile  = try {
    RandomAccessFile(this, "rw").channel
} catch (t: Throwable) {
    if (t is SecurityException) {
        val e = AccessDeniedException(this, reason = "SecurityException")
        e.addSuppressed(t)
        throw e
    }
    if (t is FileNotFoundException) {
        if (t.message?.contains("denied") != true) throw t
        val e = AccessDeniedException(this, reason = "Permission denied")
        e.addSuppressed(t)
        throw e
    }
    throw t.wrapIOException()
}
