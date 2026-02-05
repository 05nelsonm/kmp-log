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

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
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
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.exists2
import io.matthewnelson.kmp.file.openRead
import io.matthewnelson.kmp.file.openReadWrite
import io.matthewnelson.kmp.file.parentFile
import io.matthewnelson.kmp.file.resolve
import org.kotlincrypto.hash.blake2.BLAKE2s
import kotlin.random.Random

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
internal fun File.exists2Robustly(): Boolean = try {
    exists2()
} catch (_: IOException) {
    var s: FileStream.Read? = null
    try {
        // Must exist to open O_RDONLY.
        s = openRead()
        true
    } catch (e: IOException) {
        if (e !is FileSystemException) false else {
            val r = e.reason
            when {
                r == null -> false
                r.contains("EISDIR") -> true
                r.contains("Is a directory") -> true
                else -> false
            }
        }
    } finally {
        try {
            s?.close()
        } catch (_: Throwable) {}
    }
}

/**
 * Deletes the provided [File] via [File.delete2]. In the event it is unable to be
 * deleted due to a [DirectoryNotEmptyException], a randomly generated name prefixed
 * with `.` will be used to attempt to move it there.
 *
 * **NOTE:** This should **only** be utilized on files that should NOT exist on
 * the filesystem.
 *
 * @param [buf] The array to use for creating a random name, or `null`.
 * @param [maxNewNameLen] The maximum length for a new file name.
 *
 * @return The [File] for the directory it was moved to, or `null` if [File.delete2]
 *   was successful.
 *
 * @throws [IllegalArgumentException] when:
 *   - [File.isAbsolute] is `false`
 *   - [maxNewNameLen] is less than `7` characters in length
 *   - [File.parentFile] is `null`
 *   - [buf] size is less than `32`
 * @throws [DirectoryNotEmptyException] If was unable to move to a randomly named [File].
 * */
@Throws(IllegalArgumentException::class, DirectoryNotEmptyException::class)
internal fun File.deleteOrMoveToRandomIfNonEmptyDirectory(
    buf: ByteArray?,
    maxNewNameLen: Int,
): File? {
    require(isAbsolute()) { "isAbsolute[false]" }
    val parent = parentFile
    val minBufSize = 32 // must be at LEAST 32 bytes for BLAKE2s digestInto
    require(maxNewNameLen >= 7) { "maxNewNameLen[$maxNewNameLen] < 7" }
    require(parent != null) { "parent directory must not be null" }
    if (buf != null) require(buf.size >= minBufSize) { "buf.size[${buf.size}] < $minBufSize" }

    val e: DirectoryNotEmptyException = try {
        delete2(ignoreReadOnly = true, mustExist = false)
        null
    } catch (e: IOException) {
        e as? DirectoryNotEmptyException
    } ?: return null

    val blake2 = run {
        var bitStrength = maxNewNameLen
        bitStrength--                               // . character prefix
        bitStrength /= Base16.config.maxEncodeEmit  // 2 characters per byte
        bitStrength *= Byte.SIZE_BITS               // convert to bits
        BLAKE2s(bitStrength.coerceAtMost(256))
    }

    @Suppress("LocalVariableName")
    val _buf = buf ?: ByteArray(minBufSize)
    Random.nextBytes(_buf, 0, minBufSize)
    blake2.update(_buf, 0, minBufSize)
    val len = blake2.digestInto(_buf, destOffset = 0)

    // Base16 encoder will NOT insert its line break because maximum digest
    // byte size of BLAKE2s is 32, so max 64 characters when Base16 encoded
    // which is what Base16.config.lineBreakInterval is configured with (i.e.
    // the 65th character gets prefixed with a new line).
    val randomHidden = '.' + _buf.encodeToString(Base16, 0, len)
    val dest = parent.resolve(randomHidden)

    try {
        moveLogTo(dest)
        // TODO: write a notice file of previous directory name???
        return dest
    } catch (ee: IOException) {
        if (ee is FileNotFoundException) return null
        e.addSuppressed(ee)
        throw e
    }
}

/**
 * Move a log file, taking the place of [dest]
 *
 * @param [dest] The destination to move the file to.
 *
 * @throws [FileNotFoundException] If source [File] does not exist.
 * @throws [DirectoryNotEmptyException] If [dest] is an existing non-empty directory.
 * @throws [NotDirectoryException] If source [File] is an existing directory, but
 *   [dest] exists and is **NOT** a directory.
 * @throws [FileSystemException] Otherwise.
 * */
@Throws(IOException::class)
internal expect fun File.moveLogTo(dest: File)
