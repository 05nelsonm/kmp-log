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
import io.matthewnelson.kmp.file.lastErrorToIOException
import io.matthewnelson.kmp.log.file.withTmpFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.windows.CloseHandle
import platform.windows.FALSE
import platform.windows.HANDLE
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalForeignApi::class)
class LockFileUniqueIdMingwUnitTest: LockFileUniqueIdNativeBaseTest<HANDLE>() {

    override fun File.open(): HANDLE {
        return LockFile.openHandle(this)
    }

    override fun HANDLE.close() {
        if (CloseHandle(this) == FALSE) throw lastErrorToIOException()
    }

    override fun kmpLogFileUniqueId(fd: HANDLE, uniqueId: LongArray): Int {
        val a = UIntArray(uniqueId.size) { i -> uniqueId[i].toUInt() }
        val ret = a.usePinned { pinned ->
            kmp_log_file_unique_id(fd, pinned.addressOf(0))
        }
        for (i in a.indices) {
            uniqueId[i] = a[i].toLong()
        }
        return ret
    }

    @Test
    fun givenKmpLogFileUniqueId_whenUniqueIdParameterNull_thenReturnsNeg1() = withTmpFile { tmp ->
        val fd = tmp.open()
        try {
            assertEquals(-1, kmp_log_file_unique_id(fd, null))
        } finally {
            fd.close()
        }
    }
}
