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

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.lastErrorToIOException
import io.matthewnelson.kmp.file.path
import kotlinx.cinterop.ExperimentalForeignApi
import platform.windows.ERROR_ACCESS_DENIED
import platform.windows.ERROR_ALREADY_EXISTS
import platform.windows.ERROR_DIR_NOT_EMPTY
import platform.windows.ERROR_FILE_EXISTS
import platform.windows.ERROR_FILE_NOT_FOUND
import platform.windows.ERROR_NOACCESS
import platform.windows.ERROR_NOT_EMPTY
import platform.windows.ERROR_OPERATION_ABORTED
import platform.windows.ERROR_PATH_NOT_FOUND
import platform.windows.ERROR_SHARING_VIOLATION
import platform.windows.GetLastError
import platform.windows.MOVEFILE_REPLACE_EXISTING
import platform.windows.MoveFileExA

@Throws(IOException::class)
internal actual fun File.moveLogTo(dest: File) {
    val ret = MoveFileExA(
        lpExistingFileName = this.path,
        lpNewFileName = dest.path,
        dwFlags = MOVEFILE_REPLACE_EXISTING.toUInt(),
    )
    if (ret != 0) return

    val error = GetLastError()
    val s = when (error.toInt()) {
        ERROR_FILE_NOT_FOUND -> "ERROR_FILE_NOT_FOUND"
        ERROR_PATH_NOT_FOUND -> "ERROR_PATH_NOT_FOUND"
        ERROR_OPERATION_ABORTED -> "ERROR_OPERATION_ABORTED"
        ERROR_ACCESS_DENIED -> "ERROR_ACCESS_DENIED"
        ERROR_SHARING_VIOLATION -> "ERROR_SHARING_VIOLATION"
        ERROR_NOACCESS -> "ERROR_NOACCESS"
        ERROR_ALREADY_EXISTS -> "ERROR_ALREADY_EXISTS"
        ERROR_FILE_EXISTS -> "ERROR_FILE_EXISTS"
        ERROR_NOT_EMPTY -> "ERROR_NOT_EMPTY"
        ERROR_DIR_NOT_EMPTY -> "ERROR_DIR_NOT_EMPTY"
        else -> "code: $error"
    }

    // TODO: Error handling to determine what error codes throw what
    println("ERROR: moveLogTo - $s")

    @OptIn(ExperimentalForeignApi::class)
    throw lastErrorToIOException(this, dest, error)
}
