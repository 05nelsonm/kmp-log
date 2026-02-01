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
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

@Suppress("ACTUAL_WITHOUT_EXPECT", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
internal actual typealias FileLock = java.nio.channels.FileLock
@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias LockFile = java.nio.channels.FileChannel

@Throws(IOException::class)
internal actual inline fun File.openLockFile(): LockFile = openNioFileChannel()

@Suppress("UNUSED")
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
    // mode: 666
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

// java.nio.channels.FileLock requires non-null FileChannel
// for first parameter. This satisfies that requirement...
@Suppress("RedundantNullableReturnType")
private object InvalidLockFile: LockFile() {
    override fun read(p0: ByteBuffer?): Int = error("unused")
    override fun read(p0: Array<out ByteBuffer?>?, p1: Int, p2: Int): Long = error("unused")
    override fun write(p0: ByteBuffer?): Int = error("unused")
    override fun write(p0: Array<out ByteBuffer?>?, p1: Int, p2: Int): Long = error("unused")
    override fun position(): Long = error("unused")
    override fun position(p0: Long): LockFile? = error("unused")
    override fun size(): Long = error("unused")
    override fun truncate(p0: Long): LockFile? = error("unused")
    override fun force(p0: Boolean) { error("unused") }
    override fun transferTo(p0: Long, p1: Long, p2: WritableByteChannel?): Long = error("unused")
    override fun transferFrom(p0: ReadableByteChannel?, p1: Long, p2: Long): Long = error("unused")
    override fun read(p0: ByteBuffer?, p1: Long): Int = error("unused")
    override fun write(p0: ByteBuffer?, p1: Long): Int = error("unused")
    override fun map(p0: MapMode?, p1: Long, p2: Long): MappedByteBuffer? = error("unused")
    override fun lock(p0: Long, p1: Long, p2: Boolean): FileLock? = error("unused")
    override fun tryLock(p0: Long, p1: Long, p2: Boolean): FileLock? = error("unused")
    override fun implCloseChannel() {}
}

internal actual object InvalidFileLock: FileLock(
    /* channel = */ InvalidLockFile,
    /* position = */ FILE_LOCK_POS_LOG,
    /* size = */ FILE_LOCK_SIZE,
    /* shared = */ false,
) {

    actual override fun isValid(): Boolean = false
    actual override fun release() {}

    init {
        try {
            channel()?.close()
        } catch (_: Throwable) {}
    }
}
