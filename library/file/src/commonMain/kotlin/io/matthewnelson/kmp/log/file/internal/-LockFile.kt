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
import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.chmod2

@Throws(IOException::class)
internal inline fun File.openLockFileRobustly(): LockFile = try {
    openLockFile()
} catch (e: AccessDeniedException) {
    try {
        chmod2("600")
    } catch (ee: IOException) {
        e.addSuppressed(ee)
        throw e
    }
    try {
        openLockFile()
    } catch (ee: IOException) {
        e.addSuppressed(ee)
        throw e
    }
}

internal const val FILE_LOCK_SIZE = 1L
// Bytes 0-1 are locked when writing to the log file
internal const val FILE_LOCK_POS_LOG = 0L
// Bytes 1-2 are locked when performing a log rotation
internal const val FILE_LOCK_POS_ROTATE = FILE_LOCK_POS_LOG + FILE_LOCK_SIZE

@Throws(IllegalStateException::class, IOException::class)
internal inline fun LockFile.lockLog(): FileLock = lock(position = FILE_LOCK_POS_LOG, size = FILE_LOCK_SIZE)
@Throws(IllegalStateException::class, IOException::class)
internal inline fun LockFile.tryLockLog(): FileLock? = tryLock(position = FILE_LOCK_POS_LOG, size = FILE_LOCK_SIZE)

@Throws(IllegalStateException::class, IOException::class)
internal inline fun LockFile.lockRotate(): FileLock = lock(position = FILE_LOCK_POS_ROTATE, size = FILE_LOCK_SIZE)
@Throws(IllegalStateException::class, IOException::class)
internal inline fun LockFile.tryLockRotate(): FileLock? = tryLock(position = FILE_LOCK_POS_ROTATE, size = FILE_LOCK_SIZE)

@Throws(IOException::class)
internal expect inline fun File.openLockFile(): LockFile

@Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
internal expect inline fun LockFile.lock(position: Long, size: Long): FileLock

@Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
internal expect inline fun LockFile.tryLock(position: Long, size: Long): FileLock?

internal expect abstract class FileLock {

    fun position(): Long
    fun size(): Long
    abstract fun isValid(): Boolean

    @Throws(IOException::class)
    abstract fun release()

    final override fun toString(): String
}

internal expect abstract class LockFile: Closeable {
    final override fun close()
}
