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
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import platform.posix.errno
import kotlin.test.Test

@OptIn(ExperimentalForeignApi::class)
class LockFileUniqueIdUnixUnitTest: LockFileUniqueIdNativeBaseTest<Int>() {

    override fun File.open(): Int {
        return LockFile.openFd(this)
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
