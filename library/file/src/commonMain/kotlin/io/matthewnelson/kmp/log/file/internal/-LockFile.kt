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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

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

// There are no guarantees as to the order of acquisition when using the
// blocking lock function (i.e. no fairness or FIFO). In addition to that,
// you are unable to interrupt the call via closure; blocking I/O will
// continue endlessly until acquisition or process termination. That's kind
// of wild (and dangerous?) with file locks because if another process
// obtains one on our requested range maliciously and does not release it,
// we would end up blocking forever.
//
// If failure was attributed to timing out, the thrown IOException's cause
// will be a TimeoutCancellationException.
@Throws(CancellationException::class, IllegalArgumentException::class, IllegalStateException::class, IOException::class)
internal suspend fun LockFile.lockNonBlock(position: Long, size: Long, timeout: Duration): FileLock {
    // Try once first.
    tryLock(position, size)?.let { return it }

    var lock: FileLock? = null
    var threw: CancellationException? = null
    try {
        // Can throw a TimeoutCancellationException on lambda closure, so to
        // prevent a runaway FileLock we always want to hoist the result out
        // of block lambda (never set variable with its return value) while
        // ignoring any exceptions if the result is non-null (i.e. the lock
        // acquisition was actually successful, even in the face of timing out).
        withTimeout(timeout) {
            var i = 0
            while (lock == null) {
                if (i++ == 50) {
                    i = 0
                    yield()
                }
                lock = tryLock(position, size)
            }
        }
    } catch (t: CancellationException) {
        threw = t
    }

    lock?.let { return it }
    throw IOException("Failed to acquire FileLock on [$position:$size]", threw)
}

@Throws(IOException::class)
internal expect inline fun File.openLockFile(): LockFile

@Suppress("UNUSED")
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

// An always valid FileLock
internal expect class StubFileLock: FileLock {
    override fun isValid(): Boolean
    override fun release()
}

// An always invalid FileLock
internal expect object InvalidFileLock: FileLock {
    override fun isValid(): Boolean
    override fun release()
}

internal expect abstract class LockFile: Closeable {
    internal fun isOpen(): Boolean
    final override fun close()
}

// A stub which always returns StubFileLock.
internal expect object StubLockFile: LockFile {
    override fun toString(): String
}
