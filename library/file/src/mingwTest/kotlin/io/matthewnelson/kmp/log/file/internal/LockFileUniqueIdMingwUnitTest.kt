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
import io.matthewnelson.kmp.file.path
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.convert
import platform.windows.CloseHandle
import platform.windows.CreateFileA
import platform.windows.FALSE
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OPEN_ALWAYS
import kotlin.test.Test

@Suppress("UNUSED")
@OptIn(ExperimentalForeignApi::class)
class LockFileUniqueIdMingwUnitTest: LockFileUniqueIdNativeBaseTest<HANDLE>() {

    override fun File.open(): HANDLE {
        val handle = CreateFileA(
            lpFileName = path,
            dwDesiredAccess = (GENERIC_READ.toInt() or GENERIC_WRITE).convert(),
            dwShareMode = (FILE_SHARE_DELETE or FILE_SHARE_READ or FILE_SHARE_WRITE).convert(),
            lpSecurityAttributes = null,
            dwCreationDisposition = OPEN_ALWAYS.convert(),
            dwFlagsAndAttributes = FILE_ATTRIBUTE_NORMAL.convert(),
            hTemplateFile = null,
        )
        if (handle == null || handle == INVALID_HANDLE_VALUE) throw lastErrorToIOException(this)
        return handle
    }

    override fun HANDLE.close() {
        if (CloseHandle(this) == FALSE) throw lastErrorToIOException()
    }

    override fun kmpLogFileUniqueId(fd: HANDLE, uniqueId: CPointer<ULongVar>?): Int {
        return kmp_log_file_unique_id(fd, uniqueId)
    }

    @Test
    fun stub() {}
}
