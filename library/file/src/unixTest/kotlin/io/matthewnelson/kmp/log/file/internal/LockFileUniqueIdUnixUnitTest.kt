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
package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.path
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import platform.posix.EINTR
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.S_IRUSR
import platform.posix.S_IWUSR
import platform.posix.errno
import kotlin.test.Test

@OptIn(ExperimentalForeignApi::class)
class LockFileUniqueIdUnixUnitTest: LockFileUniqueIdNativeBaseTest<Int>() {

    override fun File.open(): Int {
        // No O_TRUNC because if a lock is held by another
        // process, it could cause problems.
        val flags = O_RDWR or O_CREAT or O_CLOEXEC
        val mode = S_IRUSR or S_IWUSR // 600
        var fd: Int
        do {
            fd = platform.posix.open(path, flags, mode)
        } while (fd == -1 && errno == EINTR)
        if (fd == -1) throw errnoToIOException(errno, this)
        return fd
    }

    override fun Int.close() {
        if (platform.posix.close(this) != 0) throw errnoToIOException(errno)
    }

    override fun kmpLogFileUniqueId(fd: Int, uniqueId: CPointer<ULongVar>?): Int {
        return kmp_log_file_unique_id(fd, uniqueId)
    }

    @Test
    fun stub() {}
}
